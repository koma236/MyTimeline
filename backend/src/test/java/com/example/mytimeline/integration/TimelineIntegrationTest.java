package com.example.mytimeline.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * タイムライン（F02）を HTTP から DB まで通して検証する。
 *
 * <p>設計技法:
 * <ul>
 *   <li>同値分割: following はフォロー先の投稿だけ、all は全員の投稿</li>
 *   <li>境界値: 既定 20 件に対して 21 件 → 1 ページ目 20 件 + nextCursor、2 ページ目 1 件 + null。
 *       limit の上限 50 を超える要求は 50 に切り詰められる</li>
 *   <li>集計列: いいね・コメントの後に likeCount / commentCount / likedByMe が実データと一致する</li>
 * </ul>
 * </p>
 */
class TimelineIntegrationTest extends IntegrationTestBase {

    private String getTimeline(Session session, String tab, String query) throws Exception {
        return mockMvc.perform(get("/api/timeline/" + tab + query).header("Authorization", session.bearer()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private static List<Integer> postIds(String body) {
        return JsonPath.read(body, "$.posts[*].id");
    }

    private void follow(Session follower, Session followee) throws Exception {
        mockMvc.perform(post("/api/users/" + followee.userId() + "/follow").header("Authorization", follower.bearer()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("同値分割: following は「自分＋フォロー先」の投稿を新しい順に返し、未フォローの投稿は含まない（F02）")
    void followingTimelineContainsSelfAndFollowees() throws Exception {
        Session alice = signup("alice");
        Session bob = signup("bob");
        Session carol = signup("carol");
        long bob1 = createPost(bob, "bob 1");
        long self = createPost(alice, "alice 自身（投稿直後に見えないと投稿できたか分からないため含める）");
        createPost(carol, "carol（未フォロー）");
        long bob2 = createPost(bob, "bob 2");
        follow(alice, bob);

        String body = getTimeline(alice, "following", "");

        assertThat(postIds(body)).containsExactly((int) bob2, (int) self, (int) bob1);
        assertThat(JsonPath.<Object>read(body, "$.nextCursor")).isNull();
    }

    @Test
    @DisplayName("同値分割: 誰もフォローしていなければ following は自分の投稿だけ、all には全員の投稿が出る")
    void allTimelineContainsEveryone() throws Exception {
        Session alice = signup("alice");
        Session bob = signup("bob");
        long a1 = createPost(alice, "a");
        long b1 = createPost(bob, "b");

        assertThat(postIds(getTimeline(alice, "following", ""))).containsExactly((int) a1);
        assertThat(postIds(getTimeline(alice, "all", ""))).containsExactly((int) b1, (int) a1);
    }

    @Test
    @DisplayName("境界値: 21 件あると 1 ページ目は 20 件 + nextCursor、cursor で取った 2 ページ目は 1 件 + null")
    void paginatesAtDefaultLimit() throws Exception {
        Session alice = signup("alice");
        long oldest = createPost(alice, "post 0");
        for (int i = 1; i <= 20; i++) {
            createPost(alice, "post " + i);
        }

        String firstPage = getTimeline(alice, "all", "");
        assertThat(postIds(firstPage)).hasSize(20);
        assertThat(postIds(firstPage)).doesNotContain((int) oldest);
        Number nextCursor = JsonPath.read(firstPage, "$.nextCursor");
        assertThat(nextCursor).isNotNull();

        String secondPage = getTimeline(alice, "all", "?cursor=" + nextCursor);
        assertThat(postIds(secondPage)).containsExactly((int) oldest);
        assertThat(JsonPath.<Object>read(secondPage, "$.nextCursor")).isNull();
    }

    @Test
    @DisplayName("境界値: limit=1 は 1 件 + nextCursor、件数ちょうどの limit は nextCursor が null")
    void explicitLimit() throws Exception {
        Session alice = signup("alice");
        createPost(alice, "1");
        long newest = createPost(alice, "2");

        String limited = getTimeline(alice, "all", "?limit=1");
        assertThat(postIds(limited)).containsExactly((int) newest);
        assertThat(JsonPath.<Object>read(limited, "$.nextCursor")).isNotNull();

        String exact = getTimeline(alice, "all", "?limit=2");
        assertThat(postIds(exact)).hasSize(2);
        assertThat(JsonPath.<Object>read(exact, "$.nextCursor")).isNull();
    }

    @Test
    @DisplayName("境界値: limit=51 は上限の 50 に切り詰められる")
    void limitIsCappedAtFifty() throws Exception {
        Session alice = signup("alice");
        for (int i = 0; i < 51; i++) {
            createPost(alice, "post " + i);
        }

        String body = getTimeline(alice, "all", "?limit=51");

        assertThat(postIds(body)).hasSize(50);
        assertThat(JsonPath.<Object>read(body, "$.nextCursor")).isNotNull();
    }

    @Test
    @DisplayName("集計列: いいねとコメントの後、タイムラインの行に件数と自分のいいね状態が反映される")
    void aggregatesReflectLikesAndComments() throws Exception {
        Session alice = signup("alice");
        Session bob = signup("bob");
        long postId = createPost(bob, "bob の投稿");
        mockMvc.perform(post("/api/posts/" + postId + "/like").header("Authorization", alice.bearer()))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"コメント\"}")
                .header("Authorization", alice.bearer()))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/timeline/all").header("Authorization", alice.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.posts[0].likeCount").value(1))
            .andExpect(jsonPath("$.posts[0].commentCount").value(1))
            .andExpect(jsonPath("$.posts[0].likedByMe").value(true));
        // 見る人が変わると likedByMe も変わる
        mockMvc.perform(get("/api/timeline/all").header("Authorization", bob.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.posts[0].likeCount").value(1))
            .andExpect(jsonPath("$.posts[0].likedByMe").value(false));
    }

    @Test
    @DisplayName("ユーザーの投稿一覧（/users/{username}/posts）はそのユーザーの投稿だけを返す")
    void userPostsAreIsolated() throws Exception {
        Session alice = signup("alice");
        Session bob = signup("bob");
        long a1 = createPost(alice, "a");
        createPost(bob, "b");

        mockMvc.perform(get("/api/users/alice/posts").header("Authorization", bob.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.posts.length()").value(1))
            .andExpect(jsonPath("$.posts[0].id").value(a1));
    }
}
