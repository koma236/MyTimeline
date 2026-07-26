package com.example.mytimeline.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mytimeline.config.SecurityConfig;
import com.example.mytimeline.dto.AuthResponse;
import com.example.mytimeline.dto.LoginRequest;
import com.example.mytimeline.dto.SignupRequest;
import com.example.mytimeline.dto.UserResponse;
import com.example.mytimeline.exception.DuplicateFieldException;
import com.example.mytimeline.exception.InvalidCredentialsException;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.security.JwtService;
import com.example.mytimeline.service.AuthService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String VALID_TOKEN = "valid.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    private static UserResponse userResponse() {
        return new UserResponse(1L, "taro", "山田太郎", "taro@example.com", null, LocalDateTime.now());
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("新規登録が成功すると 201 とトークンを返す")
    void signupReturnsCreated() throws Exception {
        when(authService.signup(any())).thenReturn(new AuthResponse("token", userResponse()));

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SignupRequest("taro", "山田太郎", "taro@example.com", "password123"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").value("token"))
            .andExpect(jsonPath("$.user.username").value("taro"))
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("バリデーション NG は 400 で項目ごとのエラーを返す")
    void signupReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                // username は記号入り、password は 7 文字、email は形式不正
                .content(json(new SignupRequest("ta-ro!", "山田太郎", "not-an-email", "short12"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.username").exists())
            .andExpect(jsonPath("$.fieldErrors.email").exists())
            .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("ユーザー名重複は 409 で username のエラーを返す")
    void signupReturnsConflict() throws Exception {
        when(authService.signup(any()))
            .thenThrow(new DuplicateFieldException("username", "このユーザー名は既に使用されています"));

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SignupRequest("taro", "山田太郎", "taro@example.com", "password123"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.fieldErrors.username").value("このユーザー名は既に使用されています"));
    }

    @Test
    @DisplayName("ログイン失敗は 401 で理由を明示しないメッセージを返す")
    void loginReturnsUnauthorized() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new LoginRequest("taro@example.com", "wrongpassword"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(InvalidCredentialsException.MESSAGE))
            .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("トークンなしで /me を呼ぶと 401 を JSON で返す")
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("無効なトークンで /me を呼ぶと 401 を返す")
    void meRejectsInvalidToken() throws Exception {
        when(jwtService.parseToken("broken")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer broken"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("有効なトークンで /me を呼ぶとログイン中ユーザーを返す")
    void meReturnsCurrentUser() throws Exception {
        when(jwtService.parseToken(VALID_TOKEN)).thenReturn(Optional.of(new CurrentUser(1L, "taro")));
        when(authService.getById(1L)).thenReturn(userResponse());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("taro"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("ログアウトは 204 を返す")
    void logoutReturnsNoContent() throws Exception {
        when(jwtService.parseToken(VALID_TOKEN)).thenReturn(Optional.of(new CurrentUser(1L, "taro")));

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isNoContent());
    }
}
