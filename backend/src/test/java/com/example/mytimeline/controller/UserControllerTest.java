package com.example.mytimeline.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mytimeline.config.SecurityConfig;
import com.example.mytimeline.dto.ProfileResponse;
import com.example.mytimeline.dto.UpdateProfileRequest;
import com.example.mytimeline.dto.UserResponse;
import com.example.mytimeline.dto.UserSearchResponse;
import com.example.mytimeline.dto.UserSummary;
import com.example.mytimeline.exception.ProfileNotFoundException;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.security.JwtService;
import com.example.mytimeline.service.UserService;
import com.example.mytimeline.storage.InvalidImageException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * プロフィールエンドポイントのテスト。
 *
 * <p>他のコントローラと違い 403 のケースが無い。編集系は {@code /api/users/me} に
 * 固定されていて、他人のプロフィールを書き換える経路そのものが存在しないため。</p>
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final Long CURRENT_USER_ID = 1L;
    private static final String USERNAME = "taro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void authenticate() {
        when(jwtService.parseToken(VALID_TOKEN))
            .thenReturn(Optional.of(new CurrentUser(CURRENT_USER_ID, USERNAME)));
    }

    @Test
    @DisplayName("プロフィール取得は 200 でユーザー情報を返す")
    void getProfileReturnsUser() throws Exception {
        when(userService.getProfile(USERNAME, CURRENT_USER_ID)).thenReturn(profileResponse());

        mockMvc.perform(get("/api/users/taro").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(USERNAME))
            .andExpect(jsonPath("$.displayName").value("山田太郎"))
            .andExpect(jsonPath("$.avatarUrl").value("https://example.com/signed"));
    }

    @Test
    @DisplayName("プロフィールにメールアドレスは含まれない")
    void getProfileDoesNotExposeEmail() throws Exception {
        // 他人のプロフィールは誰でも開けるので、UserResponse をそのまま返すと個人情報が漏れる
        when(userService.getProfile(USERNAME, CURRENT_USER_ID)).thenReturn(profileResponse());

        mockMvc.perform(get("/api/users/taro").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    @DisplayName("存在しないユーザーのプロフィールは 404 になる")
    void getProfileReturnsNotFound() throws Exception {
        when(userService.getProfile("unknown", CURRENT_USER_ID)).thenThrow(new ProfileNotFoundException());

        mockMvc.perform(get("/api/users/unknown").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value(ProfileNotFoundException.MESSAGE));
    }

    @Test
    @DisplayName("プロフィールにフォロー中数・フォロワー数とフォロー状態が含まれる")
    void getProfileIncludesFollowCounts() throws Exception {
        when(userService.getProfile(USERNAME, CURRENT_USER_ID)).thenReturn(profileResponse());

        mockMvc.perform(get("/api/users/taro").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.followingCount").value(45))
            .andExpect(jsonPath("$.followerCount").value(120))
            .andExpect(jsonPath("$.followingByMe").value(true));
    }

    @Test
    @DisplayName("ユーザー検索は 200 で結果一覧と次カーソルを返す")
    void searchReturnsUsers() throws Exception {
        when(userService.searchUsers("taro", CURRENT_USER_ID, null, null))
            .thenReturn(new UserSearchResponse(List.of(userSummary()), 5L));

        mockMvc.perform(get("/api/users/search")
                .param("q", "taro")
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.users[0].username").value(USERNAME))
            .andExpect(jsonPath("$.users[0].followingByMe").value(false))
            .andExpect(jsonPath("$.nextCursor").value(5));
    }

    @Test
    @DisplayName("検索結果にメールアドレスは含まれない")
    void searchDoesNotExposeEmail() throws Exception {
        when(userService.searchUsers("taro", CURRENT_USER_ID, null, null))
            .thenReturn(new UserSearchResponse(List.of(userSummary()), null));

        mockMvc.perform(get("/api/users/search")
                .param("q", "taro")
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.users[0].email").doesNotExist());
    }

    @Test
    @DisplayName("検索語が無くても 200 で新着ユーザーを返す")
    void searchWithoutQueryIsAllowed() throws Exception {
        when(userService.searchUsers(null, CURRENT_USER_ID, null, null))
            .thenReturn(new UserSearchResponse(List.of(), null));

        mockMvc.perform(get("/api/users/search").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.users").isEmpty());
    }

    @Test
    @DisplayName("検索語が上限を超えると 400 になる")
    void searchRejectsTooLongQuery() throws Exception {
        mockMvc.perform(get("/api/users/search")
                .param("q", "a".repeat(51))
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("検索キーワードは50文字以内で入力してください"));
    }

    @Test
    @DisplayName("/api/users/search はプロフィール取得ではなく検索として扱われる")
    void searchTakesPrecedenceOverProfilePath() throws Exception {
        // {username} のパターンとも一致するが、固定の文字列を含むパスが優先される
        when(userService.searchUsers(null, CURRENT_USER_ID, null, null))
            .thenReturn(new UserSearchResponse(List.of(), null));

        mockMvc.perform(get("/api/users/search").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk());

        verify(userService, never()).getProfile(eq("search"), any());
    }

    @Test
    @DisplayName("プロフィール更新は 200 で更新後のユーザーを返す")
    void updateProfileReturnsUpdatedUser() throws Exception {
        when(userService.updateProfile(eq(CURRENT_USER_ID), any())).thenReturn(userResponse());

        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new UpdateProfileRequest("山田太郎", "自己紹介"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("山田太郎"));
    }

    @Test
    @DisplayName("表示名が空のプロフィール更新は 400 になる")
    void updateProfileRejectsBlankDisplayName() throws Exception {
        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new UpdateProfileRequest("  ", "自己紹介"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.displayName").value("表示名を入力してください"));
    }

    @Test
    @DisplayName("自己紹介が上限を超えると 400 になる")
    void updateProfileRejectsTooLongBio() throws Exception {
        String tooLong = "あ".repeat(301);

        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new UpdateProfileRequest("山田太郎", tooLong))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.bio").value("自己紹介は300文字以内で入力してください"));
    }

    @Test
    @DisplayName("アバターのアップロードは 200 で更新後のユーザーを返す")
    void updateAvatarReturnsUpdatedUser() throws Exception {
        when(userService.updateAvatar(eq(CURRENT_USER_ID), any())).thenReturn(userResponse());

        mockMvc.perform(avatarUpload(new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2}))
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.avatarUrl").value("https://example.com/signed"));
    }

    @Test
    @DisplayName("不正な画像のアップロードは 400 で項目付きのエラーを返す")
    void updateAvatarRejectsInvalidImage() throws Exception {
        when(userService.updateAvatar(eq(CURRENT_USER_ID), any()))
            .thenThrow(new InvalidImageException(InvalidImageException.UNSUPPORTED_FORMAT));

        mockMvc.perform(avatarUpload(new MockMultipartFile("file", "a.txt", "text/plain", new byte[] {1}))
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.image").value(InvalidImageException.UNSUPPORTED_FORMAT));
    }

    @Test
    @DisplayName("file パートが無いアップロードは 500 ではなく 400 になる")
    void updateAvatarRejectsMissingPart() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/users/me/avatar")
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("リクエストの形式が正しくありません"));
    }

    @Test
    @DisplayName("アバターの削除は 200 で avatarUrl が null のユーザーを返す")
    void deleteAvatarReturnsUserWithoutAvatar() throws Exception {
        when(userService.deleteAvatar(CURRENT_USER_ID)).thenReturn(userResponseWithoutAvatar());

        mockMvc.perform(delete("/api/users/me/avatar").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.avatarUrl").doesNotExist());
    }

    @Test
    @DisplayName("未認証のプロフィール取得は 401 になる")
    void getProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/taro"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("ログインが必要です"));
    }

    @Test
    @DisplayName("未認証のプロフィール更新は 401 になる")
    void updateProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new UpdateProfileRequest("山田太郎", null))))
            .andExpect(status().isUnauthorized());
    }

    /** multipart(...) の既定は POST なので、PUT は HttpMethod を明示して組み立てる。 */
    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder
        avatarUpload(MockMultipartFile file) {
        return multipart(HttpMethod.PUT, "/api/users/me/avatar").file(file);
    }

    private static ProfileResponse profileResponse() {
        return new ProfileResponse(
            CURRENT_USER_ID, USERNAME, "山田太郎", "自己紹介", "https://example.com/signed", LocalDateTime.now(),
            45L, 120L, true
        );
    }

    private static UserSummary userSummary() {
        return new UserSummary(
            2L, USERNAME, "山田太郎", "自己紹介", "https://example.com/signed", false
        );
    }

    private static UserResponse userResponse() {
        return new UserResponse(
            CURRENT_USER_ID, USERNAME, "山田太郎", "taro@example.com", "自己紹介",
            "https://example.com/signed", LocalDateTime.now()
        );
    }

    private static UserResponse userResponseWithoutAvatar() {
        return new UserResponse(
            CURRENT_USER_ID, USERNAME, "山田太郎", "taro@example.com", "自己紹介", null, LocalDateTime.now()
        );
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
