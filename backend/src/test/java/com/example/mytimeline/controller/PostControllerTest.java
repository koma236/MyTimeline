package com.example.mytimeline.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mytimeline.config.SecurityConfig;
import com.example.mytimeline.dto.PostAuthor;
import com.example.mytimeline.dto.PostRequest;
import com.example.mytimeline.dto.PostResponse;
import com.example.mytimeline.exception.PostForbiddenException;
import com.example.mytimeline.exception.PostNotFoundException;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.security.JwtService;
import com.example.mytimeline.service.PostService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
        return new PostResponse(
            10L,
            body,
            new PostAuthor(CURRENT_USER_ID, "taro", "山田太郎"),
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

    @Test
    @DisplayName("投稿の作成は 201 と作成された投稿を返す")
    void createReturnsCreated() throws Exception {
        when(postService.create(eq(CURRENT_USER_ID), any())).thenReturn(postResponse("こんにちは"));

        mockMvc.perform(post("/api/posts")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new PostRequest("こんにちは"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.body").value("こんにちは"))
            .andExpect(jsonPath("$.author.username").value("taro"))
            // 投稿者のメールアドレスは他人の投稿にも付いて回るので含めない
            .andExpect(jsonPath("$.author.email").doesNotExist());
    }

    @Test
    @DisplayName("本文が空の投稿は 400 で body のエラーを返す")
    void createRejectsBlankBody() throws Exception {
        mockMvc.perform(post("/api/posts")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new PostRequest("   "))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.body").exists());
    }

    @Test
    @DisplayName("本文が 280 文字を超える投稿は 400 になる")
    void createRejectsTooLongBody() throws Exception {
        mockMvc.perform(post("/api/posts")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new PostRequest("あ".repeat(281)))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.body").exists());
    }

    @Test
    @DisplayName("ちょうど 280 文字の投稿は受け付ける")
    void createAcceptsBoundaryLength() throws Exception {
        when(postService.create(eq(CURRENT_USER_ID), any())).thenReturn(postResponse("あ".repeat(280)));

        mockMvc.perform(post("/api/posts")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new PostRequest("あ".repeat(280)))))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("未認証の投稿作成は 401 になる")
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new PostRequest("こんにちは"))))
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
    @DisplayName("編集も本文のバリデーションを受ける")
    void updateRejectsBlankBody() throws Exception {
        mockMvc.perform(put("/api/posts/10")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new PostRequest(""))))
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
    @DisplayName("JSON として壊れた本文の投稿は 400 を共通エラー形式で返す")
    void createWithMalformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/posts")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("リクエストの形式が正しくありません"));
    }
}
