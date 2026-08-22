package com.example.mytimeline.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 投稿（F03）を HTTP から DB まで通して検証する。
 *
 * <p>設計技法:
 * <ul>
 *   <li>デシジョンテーブル（作成）: 本文 {空, あり} × 画像 {0 枚, あり} → 空×0 枚だけ 400</li>
 *   <li>状態遷移: 作成 → 取得 → 編集 → 削除 → 404</li>
 *   <li>エラー推測: 他人の投稿の編集・削除は 403 で、DB の内容は変わらない</li>
 * </ul>
 * </p>
 */
class PostFlowIntegrationTest extends IntegrationTestBase {

    private long countPosts() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM posts", Long.class);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("デシジョンテーブル: 本文あり × 画像 0 枚 → 201。DB に本文と投稿者が入り、GET で読み戻せる")
    void createTextOnlyPost() throws Exception {
        Session alice = signup("alice");

        long postId = createPost(alice, "こんにちは");

        assertThat(jdbc.queryForObject("SELECT body FROM posts WHERE id = ?", String.class, postId)).isEqualTo("こんにちは");
        mockMvc.perform(get("/api/posts/" + postId).header("Authorization", alice.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").value("こんにちは"))
            .andExpect(jsonPath("$.author.id").value(alice.userId()))
            .andExpect(jsonPath("$.author.username").value("alice"))
            .andExpect(jsonPath("$.imageUrls").isEmpty())
            .andExpect(jsonPath("$.likeCount").value(0))
            .andExpect(jsonPath("$.commentCount").value(0))
            .andExpect(jsonPath("$.likedByMe").value(false));
    }

    @Test
    @DisplayName("デシジョンテーブル: 本文あり × 画像あり → 201。S3 に put され post_images に行が入り、imageUrls が枚数分返る")
    void createPostWithImages() throws Exception {
        Session alice = signup("alice");

        mockMvc.perform(multipart("/api/posts")
                .file(pngPart("images", "a.png"))
                .file(pngPart("images", "b.png"))
                .param("body", "画像付き")
                .header("Authorization", alice.bearer()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.imageUrls.length()").value(2))
            .andExpect(jsonPath("$.imageUrls[0]").value("https://s3.test/posts/test/image.png"));

        verify(storageService, times(2)).put(eq("posts/test/image.png"), any(byte[].class), eq("image/png"));
        Long imageRows = jdbc.queryForObject("SELECT COUNT(*) FROM post_images", Long.class);
        assertThat(imageRows).isEqualTo(2);
    }

    @Test
    @DisplayName("デシジョンテーブル: 本文なし × 画像あり → 201（画像だけの投稿は成立する）")
    void createImageOnlyPost() throws Exception {
        Session alice = signup("alice");

        mockMvc.perform(multipart("/api/posts")
                .file(pngPart("images", "a.png"))
                .header("Authorization", alice.bearer()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.imageUrls.length()").value(1));
    }

    @Test
    @DisplayName("デシジョンテーブル: 本文なし × 画像 0 枚 → 400。DB には何も入らない")
    void createEmptyPostIsRejected() throws Exception {
        Session alice = signup("alice");

        mockMvc.perform(multipart("/api/posts")
                .param("body", "   ")
                .header("Authorization", alice.bearer()))
            .andExpect(status().isBadRequest());

        assertThat(countPosts()).isZero();
    }

    @Test
    @DisplayName("境界値: 画像 5 枚は 400 で、S3 にも DB にも何も残らない")
    void tooManyImagesIsRejectedBeforeAnySideEffect() throws Exception {
        Session alice = signup("alice");

        mockMvc.perform(multipart("/api/posts")
                .file(pngPart("images", "1.png"))
                .file(pngPart("images", "2.png"))
                .file(pngPart("images", "3.png"))
                .file(pngPart("images", "4.png"))
                .file(pngPart("images", "5.png"))
                .param("body", "多すぎ")
                .header("Authorization", alice.bearer()))
            .andExpect(status().isBadRequest());

        verify(storageService, times(0)).put(any(), any(), any());
        assertThat(countPosts()).isZero();
    }

    @Test
    @DisplayName("状態遷移: 編集で本文が DB ごと変わり、削除後は 404 になる")
    void updateThenDelete() throws Exception {
        Session alice = signup("alice");
        long postId = createPost(alice, "before");

        mockMvc.perform(put("/api/posts/" + postId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"after\"}")
                .header("Authorization", alice.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").value("after"));
        assertThat(jdbc.queryForObject("SELECT body FROM posts WHERE id = ?", String.class, postId)).isEqualTo("after");

        mockMvc.perform(delete("/api/posts/" + postId).header("Authorization", alice.bearer()))
            .andExpect(status().isNoContent());

        assertThat(countPosts()).isZero();
        mockMvc.perform(get("/api/posts/" + postId).header("Authorization", alice.bearer()))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("エラー推測: 他人の投稿の編集・削除は 403 で、DB は変わらない")
    void othersCannotModifyPost() throws Exception {
        Session alice = signup("alice");
        Session bob = signup("bob");
        long postId = createPost(alice, "alice の投稿");

        mockMvc.perform(put("/api/posts/" + postId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"改ざん\"}")
                .header("Authorization", bob.bearer()))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/posts/" + postId).header("Authorization", bob.bearer()))
            .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT body FROM posts WHERE id = ?", String.class, postId)).isEqualTo("alice の投稿");
        assertThat(countPosts()).isEqualTo(1);
    }

    @Test
    @DisplayName("削除時は S3 の画像も deleteQuietly で片付けられる")
    void deletingPostRemovesImagesFromStorage() throws Exception {
        Session alice = signup("alice");
        String body = mockMvc.perform(multipart("/api/posts")
                .file(pngPart("images", "a.png"))
                .param("body", "画像付き")
                .header("Authorization", alice.bearer()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long postId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(delete("/api/posts/" + postId).header("Authorization", alice.bearer()))
            .andExpect(status().isNoContent());

        verify(storageService).deleteQuietly("posts/test/image.png");
        Long imageRows = jdbc.queryForObject("SELECT COUNT(*) FROM post_images", Long.class);
        assertThat(imageRows).isZero();
    }

    @Test
    @DisplayName("未認証では作成・取得ともに 401")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/posts").param("body", "x")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/posts/1")).andExpect(status().isUnauthorized());
    }
}
