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
import com.example.mytimeline.dto.CommentListResponse;
import com.example.mytimeline.dto.CommentRequest;
import com.example.mytimeline.dto.CommentResponse;
import com.example.mytimeline.dto.PostAuthor;
import com.example.mytimeline.exception.CommentForbiddenException;
import com.example.mytimeline.exception.CommentNotFoundException;
import com.example.mytimeline.exception.PostNotFoundException;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.security.JwtService;
import com.example.mytimeline.service.CommentService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(CommentController.class)
@Import(SecurityConfig.class)
class CommentControllerTest {

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final Long CURRENT_USER_ID = 1L;
    private static final Long POST_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommentService commentService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void authenticate() {
        when(jwtService.parseToken(VALID_TOKEN))
            .thenReturn(Optional.of(new CurrentUser(CURRENT_USER_ID, "taro")));
    }

    private static CommentResponse commentResponse(Long id, String body) {
        return new CommentResponse(
            id,
            POST_ID,
            body,
            new PostAuthor(CURRENT_USER_ID, "taro", "山田太郎"),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("コメント一覧は 200 でコメントと次のカーソルを返す")
    void listReturnsComments() throws Exception {
        when(commentService.list(POST_ID, null, null)).thenReturn(
            new CommentListResponse(List.of(commentResponse(1L, "いい天気ですね")), 1L));

        mockMvc.perform(get("/api/posts/10/comments").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].id").value(1))
            .andExpect(jsonPath("$.comments[0].body").value("いい天気ですね"))
            .andExpect(jsonPath("$.comments[0].author.username").value("taro"))
            // 投稿者のメールアドレスは他人のコメントにも付いて回るので含めない
            .andExpect(jsonPath("$.comments[0].author.email").doesNotExist())
            .andExpect(jsonPath("$.nextCursor").value(1));
    }

    @Test
    @DisplayName("cursor と limit はそのままサービスへ渡される")
    void listPassesCursorAndLimit() throws Exception {
        when(commentService.list(POST_ID, 5L, 10))
            .thenReturn(new CommentListResponse(List.of(), null));

        mockMvc.perform(get("/api/posts/10/comments")
                .param("cursor", "5")
                .param("limit", "10")
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk());

        verify(commentService).list(POST_ID, 5L, 10);
    }

    @Test
    @DisplayName("最終ページでは nextCursor が null になる")
    void listReturnsNullCursorOnLastPage() throws Exception {
        when(commentService.list(POST_ID, null, null))
            .thenReturn(new CommentListResponse(List.of(commentResponse(1L, "本文")), null));

        mockMvc.perform(get("/api/posts/10/comments").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("存在しない投稿のコメント一覧は 404 になる")
    void listReturnsNotFound() throws Exception {
        when(commentService.list(999L, null, null)).thenThrow(new PostNotFoundException());

        mockMvc.perform(get("/api/posts/999/comments").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value(PostNotFoundException.MESSAGE));
    }

    @Test
    @DisplayName("コメントの作成は 201 と作成されたコメントを返す")
    void createReturnsCreated() throws Exception {
        when(commentService.create(eq(POST_ID), eq(CURRENT_USER_ID), any()))
            .thenReturn(commentResponse(7L, "いい天気ですね"));

        mockMvc.perform(post("/api/posts/10/comments")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CommentRequest("いい天気ですね"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.postId").value(10))
            .andExpect(jsonPath("$.body").value("いい天気ですね"));
    }

    @Test
    @DisplayName("本文が空のコメントは 400 で body のエラーを返す")
    void createRejectsBlankBody() throws Exception {
        mockMvc.perform(post("/api/posts/10/comments")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CommentRequest("   "))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.body").exists());
    }

    @Test
    @DisplayName("本文が 500 文字を超えるコメントは 400 になる")
    void createRejectsTooLongBody() throws Exception {
        mockMvc.perform(post("/api/posts/10/comments")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CommentRequest("あ".repeat(501)))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.body").exists());
    }

    @Test
    @DisplayName("ちょうど 500 文字のコメントは受け付ける")
    void createAcceptsBoundaryLength() throws Exception {
        when(commentService.create(eq(POST_ID), eq(CURRENT_USER_ID), any()))
            .thenReturn(commentResponse(7L, "あ".repeat(500)));

        mockMvc.perform(post("/api/posts/10/comments")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CommentRequest("あ".repeat(500)))))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("未認証のコメント作成は 401 になる")
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/posts/10/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CommentRequest("いい天気ですね"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("ログインが必要です"));
    }

    @Test
    @DisplayName("コメントの編集は 200 で更新後のコメントを返す")
    void updateReturnsUpdatedComment() throws Exception {
        when(commentService.update(eq(7L), eq(CURRENT_USER_ID), any()))
            .thenReturn(commentResponse(7L, "編集後"));

        mockMvc.perform(put("/api/comments/7")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CommentRequest("編集後"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").value("編集後"));
    }

    @Test
    @DisplayName("他人のコメントの編集は 403 になる")
    void updateReturnsForbidden() throws Exception {
        when(commentService.update(eq(7L), eq(CURRENT_USER_ID), any()))
            .thenThrow(new CommentForbiddenException());

        mockMvc.perform(put("/api/comments/7")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CommentRequest("書き換え"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value(CommentForbiddenException.MESSAGE));
    }

    @Test
    @DisplayName("編集も本文のバリデーションを受ける")
    void updateRejectsBlankBody() throws Exception {
        mockMvc.perform(put("/api/comments/7")
                .header("Authorization", "Bearer " + VALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CommentRequest(""))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.body").exists());
    }

    @Test
    @DisplayName("コメントの削除は 204 を返す")
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/comments/7").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isNoContent());

        verify(commentService).delete(7L, CURRENT_USER_ID);
    }

    @Test
    @DisplayName("他人のコメントの削除は 403 になる")
    void deleteReturnsForbidden() throws Exception {
        doThrow(new CommentForbiddenException()).when(commentService).delete(7L, CURRENT_USER_ID);

        mockMvc.perform(delete("/api/comments/7").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value(CommentForbiddenException.MESSAGE));
    }

    @Test
    @DisplayName("存在しないコメントの削除は 404 になる")
    void deleteReturnsNotFound() throws Exception {
        doThrow(new CommentNotFoundException()).when(commentService).delete(999L, CURRENT_USER_ID);

        mockMvc.perform(delete("/api/comments/999").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value(CommentNotFoundException.MESSAGE));
    }
}
