package com.example.mytimeline.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * いいね（F05）・コメント（F04）・フォロー（F06）・プロフィール / 検索（F07）を HTTP から DB まで通して検証する。
 *
 * <p>設計技法: 状態遷移（いいね・フォローの付与と取り消し、重複は冪等）、
 * エラー推測（自己フォロー・他人のコメント操作・存在しない投稿）、
 * 集計（プロフィールのフォロー数が実データと一致）。</p>
 */
class SocialIntegrationTest extends IntegrationTestBase {

    private long count(String table, String where, Object... args) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class, args);
        return count == null ? 0 : count;
    }

    @Nested
    @DisplayName("いいね")
    class Like {

        @Test
        @DisplayName("状態遷移: 付与 → 再付与（冪等）→ 取り消し → 再取り消し（冪等）で likes の行数が 0→1→1→0→0")
        void likeLifecycle() throws Exception {
            Session alice = signup("alice");
            Session bob = signup("bob");
            long postId = createPost(bob, "x");
            String url = "/api/posts/" + postId + "/like";

            mockMvc.perform(post(url).header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByMe").value(true));
            mockMvc.perform(post(url).header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));
            assertThat(count("likes", "post_id = ?", postId)).isEqualTo(1);

            mockMvc.perform(delete(url).header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.likedByMe").value(false));
            mockMvc.perform(delete(url).header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0));
            assertThat(count("likes", "post_id = ?", postId)).isZero();
        }

        @Test
        @DisplayName("エラー推測: 存在しない投稿へのいいねは 404 で、行は増えない")
        void likeMissingPost() throws Exception {
            Session alice = signup("alice");

            mockMvc.perform(post("/api/posts/9999/like").header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());

            assertThat(count("likes", "1 = 1")).isZero();
        }
    }

    @Nested
    @DisplayName("コメント")
    class Comment {

        private long comment(Session session, long postId, String body) throws Exception {
            String response = mockMvc.perform(post("/api/posts/" + postId + "/comments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"" + body + "\"}")
                    .header("Authorization", session.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(postId))
                .andExpect(jsonPath("$.body").value(body))
                .andReturn().getResponse().getContentAsString();
            return ((Number) JsonPath.read(response, "$.id")).longValue();
        }

        @Test
        @DisplayName("状態遷移: 作成 → 一覧（古い順）→ 編集 → 削除 で DB とレスポンスが一致する")
        void commentLifecycle() throws Exception {
            Session alice = signup("alice");
            Session bob = signup("bob");
            long postId = createPost(alice, "x");
            long first = comment(bob, postId, "1");
            long second = comment(alice, postId, "2");

            mockMvc.perform(get("/api/posts/" + postId + "/comments").header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].id").value(first))
                .andExpect(jsonPath("$.comments[0].author.username").value("bob"))
                .andExpect(jsonPath("$.comments[1].id").value(second))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

            mockMvc.perform(put("/api/comments/" + first)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"edited\"}")
                    .header("Authorization", bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("edited"));
            assertThat(jdbc.queryForObject("SELECT body FROM comments WHERE id = ?", String.class, first)).isEqualTo("edited");

            mockMvc.perform(delete("/api/comments/" + first).header("Authorization", bob.bearer()))
                .andExpect(status().isNoContent());
            assertThat(count("comments", "post_id = ?", postId)).isEqualTo(1);
        }

        @Test
        @DisplayName("エラー推測: 他人のコメントの編集・削除は 403（投稿主であっても）")
        void othersCannotModifyComment() throws Exception {
            Session alice = signup("alice");
            Session bob = signup("bob");
            long postId = createPost(alice, "x");
            long commentId = comment(bob, postId, "bob のコメント");

            mockMvc.perform(put("/api/comments/" + commentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"改ざん\"}")
                    .header("Authorization", alice.bearer()))
                .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/comments/" + commentId).header("Authorization", alice.bearer()))
                .andExpect(status().isForbidden());

            assertThat(jdbc.queryForObject("SELECT body FROM comments WHERE id = ?", String.class, commentId))
                .isEqualTo("bob のコメント");
        }

        @Test
        @DisplayName("境界値: 空本文は 400、501 文字は 400、500 文字は 201")
        void bodyBoundaries() throws Exception {
            Session alice = signup("alice");
            long postId = createPost(alice, "x");
            String url = "/api/posts/" + postId + "/comments";

            mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"\"}")
                    .header("Authorization", alice.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.body").isString());
            mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + "あ".repeat(501) + "\"}")
                    .header("Authorization", alice.bearer()))
                .andExpect(status().isBadRequest());
            mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + "あ".repeat(500) + "\"}")
                    .header("Authorization", alice.bearer()))
                .andExpect(status().isCreated());
            assertThat(count("comments", "post_id = ?", postId)).isEqualTo(1);
        }

        @Test
        @DisplayName("エラー推測: 存在しない投稿へのコメントは 404")
        void commentOnMissingPost() throws Exception {
            Session alice = signup("alice");

            mockMvc.perform(post("/api/posts/9999/comments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"x\"}")
                    .header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("フォローとプロフィール")
    class FollowAndProfile {

        @Test
        @DisplayName("状態遷移: フォロー → 再フォロー（冪等）→ 解除 で follows の行数と followerCount が追従する")
        void followLifecycle() throws Exception {
            Session alice = signup("alice");
            Session bob = signup("bob");
            String url = "/api/users/" + bob.userId() + "/follow";

            mockMvc.perform(post(url).header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerCount").value(1))
                .andExpect(jsonPath("$.followingByMe").value(true));
            mockMvc.perform(post(url).header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerCount").value(1));
            assertThat(count("follows", "follower_id = ? AND followee_id = ?", alice.userId(), bob.userId())).isEqualTo(1);

            mockMvc.perform(delete(url).header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.followingByMe").value(false));
            assertThat(count("follows", "1 = 1")).isZero();
        }

        @Test
        @DisplayName("エラー推測: 自己フォローは 400、存在しないユーザーは 404。どちらも行は増えない")
        void selfFollowAndUnknownUser() throws Exception {
            Session alice = signup("alice");

            mockMvc.perform(post("/api/users/" + alice.userId() + "/follow").header("Authorization", alice.bearer()))
                .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/users/9999/follow").header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());

            assertThat(count("follows", "1 = 1")).isZero();
        }

        @Test
        @DisplayName("集計: プロフィールの followingCount / followerCount / followingByMe が実データと一致する")
        void profileCountsReflectFollows() throws Exception {
            Session alice = signup("alice");
            Session bob = signup("bob");
            Session carol = signup("carol");
            // alice → bob, carol → bob, bob → alice
            mockMvc.perform(post("/api/users/" + bob.userId() + "/follow").header("Authorization", alice.bearer()));
            mockMvc.perform(post("/api/users/" + bob.userId() + "/follow").header("Authorization", carol.bearer()));
            mockMvc.perform(post("/api/users/" + alice.userId() + "/follow").header("Authorization", bob.bearer()));

            mockMvc.perform(get("/api/users/bob").header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followingCount").value(1))
                .andExpect(jsonPath("$.followerCount").value(2))
                .andExpect(jsonPath("$.followingByMe").value(true));
            mockMvc.perform(get("/api/users/bob").header("Authorization", bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followingByMe").value(false));
            mockMvc.perform(get("/api/users/nobody").header("Authorization", alice.bearer()))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("プロフィール更新は users に反映され、bio は空に戻せる")
        void updateProfile() throws Exception {
            Session alice = signup("alice");

            mockMvc.perform(put("/api/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"displayName\":\"アリス\",\"bio\":\"よろしく\"}")
                    .header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("アリス"))
                .andExpect(jsonPath("$.bio").value("よろしく"));
            assertThat(jdbc.queryForObject("SELECT bio FROM users WHERE id = ?", String.class, alice.userId())).isEqualTo("よろしく");

            mockMvc.perform(put("/api/users/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"displayName\":\"アリス\",\"bio\":null}")
                    .header("Authorization", alice.bearer()))
                .andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT bio FROM users WHERE id = ?", String.class, alice.userId())).isNull();
        }

        @Test
        @DisplayName("検索はフォロー状態（followingByMe）を各ユーザーに付けて返し、自分自身も含む")
        void searchCarriesFollowState() throws Exception {
            Session alice = signup("alice_a");
            Session bob = signup("bob_a");
            signup("carol_b");
            mockMvc.perform(post("/api/users/" + bob.userId() + "/follow").header("Authorization", alice.bearer()));

            String body = mockMvc.perform(get("/api/users/search").param("q", "_a").header("Authorization", alice.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

            assertThat(JsonPath.<java.util.List<String>>read(body, "$.users[*].username"))
                .containsExactlyInAnyOrder("alice_a", "bob_a");
            assertThat(JsonPath.<java.util.List<Boolean>>read(body, "$.users[?(@.username=='bob_a')].followingByMe"))
                .containsExactly(true);
            assertThat(JsonPath.<java.util.List<Boolean>>read(body, "$.users[?(@.username=='alice_a')].followingByMe"))
                .containsExactly(false);
        }
    }
}
