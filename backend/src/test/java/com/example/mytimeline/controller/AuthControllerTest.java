package com.example.mytimeline.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mytimeline.config.SecurityConfig;
import com.example.mytimeline.dto.AuthResponse;
import com.example.mytimeline.dto.LoginRequest;
import com.example.mytimeline.dto.SignupRequest;
import com.example.mytimeline.dto.UserResponse;
import com.example.mytimeline.exception.DuplicateFieldException;
import com.example.mytimeline.exception.InvalidCredentialsException;
import com.example.mytimeline.exception.InvalidRefreshTokenException;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.security.JwtProperties;
import com.example.mytimeline.security.JwtService;
import com.example.mytimeline.security.RefreshCookieFactory;
import com.example.mytimeline.service.AuthService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthControllerTest.CookieTestConfig.class})
class AuthControllerTest {

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String REFRESH_TOKEN = "raw-refresh-token";

    /**
     * {@link RefreshCookieFactory} は {@link JwtProperties} に依存するが、
     * {@code @WebMvcTest} では {@code @ConfigurationProperties} が読み込まれないため手で組む。
     */
    @TestConfiguration
    static class CookieTestConfig {
        @Bean
        RefreshCookieFactory refreshCookieFactory() {
            return new RefreshCookieFactory(
                new JwtProperties("test-secret-key-for-unit-test-at-least-32-bytes", 15, 14, false)
            );
        }
    }

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

    private static AuthService.AuthResult authResult(String accessToken) {
        return new AuthService.AuthResult(new AuthResponse(accessToken, userResponse()), REFRESH_TOKEN);
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("新規登録が成功すると 201 とアクセストークンを返す")
    void signupReturnsCreated() throws Exception {
        when(authService.signup(any())).thenReturn(authResult("token"));

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SignupRequest("taro", "山田太郎", "taro@example.com", "password123"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").value("token"))
            .andExpect(jsonPath("$.user.username").value("taro"))
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("新規登録はリフレッシュトークンを httpOnly Cookie で返し、ボディには含めない")
    void signupSetsHttpOnlyRefreshCookie() throws Exception {
        when(authService.signup(any())).thenReturn(authResult("token"));

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SignupRequest("taro", "山田太郎", "taro@example.com", "password123"))))
            .andExpect(cookie().value("refreshToken", REFRESH_TOKEN))
            .andExpect(cookie().httpOnly("refreshToken", true))
            .andExpect(cookie().path("refreshToken", "/api/auth"))
            .andExpect(header().string("Set-Cookie", Matchers.containsString("SameSite=Lax")))
            // ボディに載せると JavaScript から読めてしまい httpOnly の意味が無くなる
            .andExpect(jsonPath("$.refreshToken").doesNotExist());
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
    @DisplayName("ログイン成功で 200・アクセストークン・リフレッシュ Cookie を返す")
    void loginReturnsTokens() throws Exception {
        when(authService.login(any())).thenReturn(authResult("token"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new LoginRequest("taro@example.com", "password123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("token"))
            .andExpect(cookie().httpOnly("refreshToken", true));
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
    @DisplayName("リフレッシュはアクセストークンなしでも Cookie だけで通り、新しい Cookie を返す")
    void refreshWorksWithCookieOnly() throws Exception {
        when(authService.refresh(REFRESH_TOKEN))
            .thenReturn(new AuthService.AuthResult(
                new AuthResponse("new-access-token", userResponse()), "rotated-refresh-token"));

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", REFRESH_TOKEN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("new-access-token"))
            .andExpect(jsonPath("$.user.username").value("taro"))
            .andExpect(cookie().value("refreshToken", "rotated-refresh-token"));
    }

    @Test
    @DisplayName("無効なリフレッシュトークンは 401 を返す")
    void refreshRejectsInvalidToken() throws Exception {
        when(authService.refresh(any())).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", "stale")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(InvalidRefreshTokenException.MESSAGE));
    }

    @Test
    @DisplayName("Cookie が無いリフレッシュも 401 になる（403 や 500 にしない）")
    void refreshWithoutCookieReturnsUnauthorized() throws Exception {
        when(authService.refresh(null)).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/auth/refresh"))
            .andExpect(status().isUnauthorized());
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
    @DisplayName("ログアウトは 204 を返し、Cookie を削除する")
    void logoutClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refreshToken", REFRESH_TOKEN)))
            .andExpect(status().isNoContent())
            .andExpect(cookie().maxAge("refreshToken", 0));

        verify(authService).logout(REFRESH_TOKEN);
    }

    @Test
    @DisplayName("アクセストークンが期限切れでもログアウトできる（Cookie だけで通る）")
    void logoutWorksWithoutAccessToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refreshToken", REFRESH_TOKEN)))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Cookie が無いログアウトも 204 を返す（トークンの存在を漏らさない）")
    void logoutWithoutCookieReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isNoContent())
            .andExpect(cookie().maxAge("refreshToken", 0));

        verify(authService).logout(null);
    }
}
