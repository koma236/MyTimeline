package com.example.mytimeline.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mytimeline.config.SecurityConfig;
import com.example.mytimeline.dto.PostAuthor;
import com.example.mytimeline.dto.PostRequest;
import com.example.mytimeline.dto.PostResponse;
import com.example.mytimeline.exception.EmptyPostException;
import com.example.mytimeline.exception.PostForbiddenException;
import com.example.mytimeline.exception.PostNotFoundException;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.security.JwtService;
import com.example.mytimeline.service.PostService;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(PostController.class)
@Import(SecurityConfig.class)
class PostControllerTest {

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final Long CURRENT_USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private JwtService jwtService;

    /** アクセストークンが有効な状態を作る。認可の判定は SecurityConfig が行う。 */
    @BeforeEach
    void authenticate() {
        when(jwtService.parseToken(VALID_TOKEN))
            .thenReturn(Optional.of(new CurrentUser(CURRENT_USER_ID, "taro")));
    }

    private static PostResponse postResponse(String body) {
        return postResponse(body, List.of());
    }

    private static PostResponse postResponse(String body, List<String> imageUrls) {
        return new PostResponse(
            10L,
            body,
            new PostAuthor(CURRENT_USER_ID, "taro", "山田太郎", null),
            imageUrls,
            3L,
            2L,
            true,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    private static MockMultipartFile image(String name) {
        return new MockMultipartFile("images", name, "image/jpeg", new byte[] {1, 2, 3});
    }

    @Test
    @DisplayName("投稿の作成は 201 と作成された投稿を返す")
    void createReturnsCreated() throws Exception {
        when(postService.create(eq(CURRENT_USER_ID), eq("こんにちは"), any()))
            .thenReturn(postResponse("こんにちは"));

        mockMvc.perform(multipart("/api/posts")
                .param("body", "こんにちは")
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.body").value("こんにちは"))
            .andExpect(jsonPath("$.author.username").value("taro"))
            // 投稿者のメールアドレスは他人の投稿にも付いて回るので含めない
            .andExpect(jsonPath("$.author.email").doesNotExist());
    }

    @Test
    @DisplayName("画像付きの投稿はファイルがサービスへ渡り、画像 URL 付きで返る")
    void createPassesImagesToService() throws Exception {
        when(postService.create(eq(CURRENT_USER_ID), eq("画像付き"), anyList()))
            .thenReturn(postResponse("画像付き", List.of("https://example.com/1.jpg")));

        mockMvc.perform(multipart("/api/posts")
                .file(image("a.jpg"))
                .param("body", "画像付き")
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.imageUrls[0]").value("https://example.com/1.jpg"));

        verify(postService).create(eq(CURRENT_USER_ID), eq("画像付き"), anyList());
    }

    @Test
    @DisplayName("本文が空で画像も無い投稿は 400 になる")
    void createRejectsEmptyPost() throws Exception {
        // 「どちらかがあればよい」の判定はサービスの責務。コントローラは例外を 400 に変換する
        when(postService.create(eq(CURRENT_USER_ID), any(), any())).thenThrow(new EmptyPostException());

        mockMvc.perform(multipart("/api/posts")
                .param("body", "   ")
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("本文を入力するか画像を添付してください"));
    }

    @Test
    @DisplayName("受け付けられない画像は 400 で image のエラーを返す")
    void createRejectsInvalidImage() throws Exception {
        when(postService.create(eq(CURRENT_USER_ID), any(), anyList()))
            .thenThrow(new InvalidImageException(InvalidImageException.TOO_MANY));

        mockMvc.perform(multipart("/api/posts")
                .file(image("a.jpg"))
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.image").value(InvalidImageException.TOO_MANY));
    }

    @Test
    @DisplayName("本文が 280 文字を超える投稿は 400 になる")
    void createRejectsTooLongBody() throws Exception {
        mockMvc.perform(multipart("/api/posts")
                .param("body", "あ".repeat(281))
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("本文は280文字以内で入力してください"));
    }

    @Test
    @DisplayName("ちょうど 280 文字の投稿は受け付ける")
    void createAcceptsBoundaryLength() throws Exception {
        when(postService.create(eq(CURRENT_USER_ID), eq("あ".repeat(280)), any()))
            .thenReturn(postResponse("あ".repeat(280)));

        mockMvc.perform(multipart("/api/posts")
                .param("body", "あ".repeat(280))
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("未認証の投稿作成は 401 になる")
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/posts").param("body", "こんにちは"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("ログインが必要です"));
    }

    @Test
    @DisplayName("投稿詳細の取得は 200 でいいね数・コメント数付きの投稿を返す")
    void getByIdReturnsPost() throws Exception {
        when(postService.getById(10L, CURRENT_USER_ID)).thenReturn(postResponse("こんにちは"));

        mockMvc.perform(get("/api/posts/10").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").value("こんにちは"))
            .andExpect(jsonPath("$.likeCount").value(3))
            .andExpect(jsonPath("$.commentCount").value(2))
            .andExpect(jsonPath("$.likedByMe").value(true));
    }

    @Test
    @DisplayName("存在しない投稿の取得は 404 になる")
    void getByIdReturnsNotFound() throws Exception {
        when(postService.getById(999L, CURRENT_USER_ID)).thenThrow(new PostNotFoundException());

        mockMvc.perform(get("/api/posts/999").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value(PostNotFoundException.MESSAGE));
    }

    @Test
    @DisplayName("投稿の編集は 200 で更新後の投稿を返す")
    void updateReturnsUpdatedPost() throws Exception {
        when(postService.update(eq(10L), eq(CURRENT_USER_ID), any())).thenReturn(postResponse("編集後"));

        mockMvc.perform(put("/api/posts/10")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new PostRequest("編集後"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").value("編集後"));
    }

    @Test
    @DisplayName("他人の投稿の編集は 403 になる")
    void updateReturnsForbidden() throws Exception {
        when(postService.update(eq(10L), eq(CURRENT_USER_ID), any()))
            .thenThrow(new PostForbiddenException());

        mockMvc.perform(put("/api/posts/10")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new PostRequest("書き換え"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value(PostForbiddenException.MESSAGE));
    }

    @Test
    @DisplayName("編集で本文を空にできるかはサービスの判定に従い、拒否なら 400 になる")
    void updateRejectsEmptyPost() throws Exception {
        // 画像が無い投稿の本文は空にできない（画像があれば空にできる）。判定はサービスの責務
        when(postService.update(eq(10L), eq(CURRENT_USER_ID), any()))
            .thenThrow(new EmptyPostException());

        mockMvc.perform(put("/api/posts/10")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new PostRequest(""))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("本文を入力するか画像を添付してください"));
    }

    @Test
    @DisplayName("編集も本文の文字数バリデーションを受ける")
    void updateRejectsTooLongBody() throws Exception {
        mockMvc.perform(put("/api/posts/10")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new PostRequest("あ".repeat(281)))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.body").exists());
    }

    @Test
    @DisplayName("投稿の削除は 204 を返す")
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/posts/10").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isNoContent());

        verify(postService).delete(10L, CURRENT_USER_ID);
    }

    @Test
    @DisplayName("他人の投稿の削除は 403 になる")
    void deleteReturnsForbidden() throws Exception {
        doThrow(new PostForbiddenException()).when(postService).delete(10L, CURRENT_USER_ID);

        mockMvc.perform(delete("/api/posts/10").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("存在しない投稿の削除は 404 になる")
    void deleteReturnsNotFound() throws Exception {
        doThrow(new PostNotFoundException()).when(postService).delete(999L, CURRENT_USER_ID);

        mockMvc.perform(delete("/api/posts/999").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("id が数値でない投稿の取得は 400 を共通エラー形式で返す")
    void getByIdWithNonNumericIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/posts/abc").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            // Spring 既定の {timestamp, status, error, path} ではなく、
            // F01 で定めた共通形式で返ること
            .andExpect(jsonPath("$.message").value("リクエストの形式が正しくありません"));
    }

    @Test
    @DisplayName("multipart でなく JSON で投稿作成を呼ぶと 400 を共通エラー形式で返す")
    void createWithJsonReturnsBadRequest() throws Exception {
        // 作成は multipart 専用（PostController 参照）。旧クライアントの JSON は
        // Spring 既定の ProblemDetail ではなく共通エラー形式で拒否されること
        mockMvc.perform(post("/api/posts")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new PostRequest("こんにちは"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("リクエストの形式が正しくありません"));
    }

    @Test
    @DisplayName("JSON として壊れた本文の編集は 400 を共通エラー形式で返す")
    void updateWithMalformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/posts/10")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("リクエストの形式が正しくありません"));
    }
}
