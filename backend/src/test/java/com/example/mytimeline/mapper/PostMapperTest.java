package com.example.mytimeline.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mytimeline.model.Comment;
import com.example.mytimeline.model.Post;
import com.example.mytimeline.model.PostImage;
import com.example.mytimeline.model.User;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * posts テーブルの SQL を H2 で検証する。
 *
 * <p>設計技法:
 * <ul>
 *   <li>デシジョンテーブル（findTimeline）: userIds {null, 空, 1 件, 複数} × cursor {null, あり}</li>
 *   <li>境界値: limit 0 / 1 / 件数ちょうど / 件数+1、cursor が最古の id</li>
 *   <li>ループ: {@code <foreach>} の IN 句が 1 要素・複数要素</li>
 *   <li>状態: deleteById で post_images / comments / likes が CASCADE で消える</li>
 * </ul>
 * 集計列（likeCount / commentCount / likedByMe）は実データを入れて 1 本の SQL で正しく出ることを見る。</p>
 */
class PostMapperTest extends MapperTestBase {

    @Autowired
    private LikeMapper likeMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private PostImageMapper postImageMapper;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = insertUser("alice");
        bob = insertUser("bob");
    }

    private static List<Long> ids(List<Post> posts) {
        return posts.stream().map(Post::getId).toList();
    }

    @Nested
    @DisplayName("登録・取得・更新・削除")
    class Crud {

        @Test
        @DisplayName("insert で id が採番され、findById で投稿者と日時が埋まる")
        void insertAndFindById() {
            Post post = insertPost(alice.getId(), "こんにちは");

            assertThat(post.getId()).isNotNull();
            Post found = postMapper.findById(post.getId(), alice.getId()).orElseThrow();
            assertThat(found.getBody()).isEqualTo("こんにちは");
            assertThat(found.getUserId()).isEqualTo(alice.getId());
            assertThat(found.getCreatedAt()).isNotNull();
            assertThat(found.getUpdatedAt()).isNotNull();
            assertThat(found.getAuthor()).isNotNull();
            assertThat(found.getAuthor().getId()).isEqualTo(alice.getId());
            assertThat(found.getAuthor().getUsername()).isEqualTo("alice");
            assertThat(found.getAuthor().getDisplayName()).isEqualTo("alice さん");
            // 画面に出さない列は JOIN でも取らない
            assertThat(found.getAuthor().getPasswordHash()).isNull();
            assertThat(found.getAuthor().getEmail()).isNull();
        }

        @Test
        @DisplayName("findById は存在しなければ empty、existsById は真偽を返す")
        void findMissingPost() {
            Post post = insertPost(alice.getId(), "x");

            assertThat(postMapper.findById(post.getId() + 1000, alice.getId())).isEmpty();
            assertThat(postMapper.existsById(post.getId())).isTrue();
            assertThat(postMapper.existsById(post.getId() + 1000)).isFalse();
        }

        @Test
        @DisplayName("集計列: いいね数・コメント数は実データと一致し、likedByMe は見る人で変わる")
        void aggregatesReflectActualRows() {
            Post post = insertPost(alice.getId(), "x");
            likeMapper.insertIgnoreDuplicate(post.getId(), bob.getId());
            insertComment(post.getId(), alice.getId());
            insertComment(post.getId(), bob.getId());

            Post seenByBob = postMapper.findById(post.getId(), bob.getId()).orElseThrow();
            Post seenByAlice = postMapper.findById(post.getId(), alice.getId()).orElseThrow();

            assertThat(seenByBob.getLikeCount()).isEqualTo(1);
            assertThat(seenByBob.getCommentCount()).isEqualTo(2);
            assertThat(seenByBob.isLikedByMe()).isTrue();
            assertThat(seenByAlice.isLikedByMe()).isFalse();
        }

        @Test
        @DisplayName("集計列: いいねもコメントも無ければ 0 / false")
        void aggregatesAreZeroWithoutRows() {
            Post post = insertPost(alice.getId(), "x");

            Post found = postMapper.findById(post.getId(), alice.getId()).orElseThrow();

            assertThat(found.getLikeCount()).isZero();
            assertThat(found.getCommentCount()).isZero();
            assertThat(found.isLikedByMe()).isFalse();
        }

        @Test
        @DisplayName("updateBody は本文を更新し updated_at を進める。存在しない id は 0 行")
        void updateBody() {
            Post post = insertPost(alice.getId(), "before");
            LocalDateTime past = LocalDateTime.of(2000, 1, 1, 0, 0);
            jdbc.update("UPDATE posts SET updated_at = ? WHERE id = ?", past, post.getId());

            assertThat(postMapper.updateBody(post.getId(), "after")).isEqualTo(1);
            Post found = postMapper.findById(post.getId(), alice.getId()).orElseThrow();
            assertThat(found.getBody()).isEqualTo("after");
            assertThat(found.getUpdatedAt()).isAfter(past);

            assertThat(postMapper.updateBody(post.getId() + 1000, "x")).isZero();
        }

        @Test
        @DisplayName("状態: deleteById で投稿が消え、画像・コメント・いいねも CASCADE で消える")
        void deleteCascadesToChildren() {
            Post post = insertPost(alice.getId(), "x");
            PostImage image = new PostImage();
            image.setPostId(post.getId());
            image.setS3Key("posts/1/a.png");
            image.setPosition(0);
            postImageMapper.insertAll(List.of(image));
            insertComment(post.getId(), bob.getId());
            likeMapper.insertIgnoreDuplicate(post.getId(), bob.getId());

            assertThat(postMapper.deleteById(post.getId())).isEqualTo(1);

            assertThat(postMapper.existsById(post.getId())).isFalse();
            assertThat(countRows("post_images", "post_id = ?", post.getId())).isZero();
            assertThat(countRows("comments", "post_id = ?", post.getId())).isZero();
            assertThat(countRows("likes", "post_id = ?", post.getId())).isZero();
            assertThat(postMapper.deleteById(post.getId())).isZero();
        }

        private void insertComment(Long postId, Long userId) {
            Comment comment = new Comment();
            comment.setPostId(postId);
            comment.setUserId(userId);
            comment.setBody("c");
            commentMapper.insert(comment);
        }
    }

    @Nested
    @DisplayName("findTimeline（カーソルページング）")
    class Timeline {

        private static final int LIMIT = 10;

        private Post a1;
        private Post b1;
        private Post a2;
        private Post b2;

        @BeforeEach
        void insertPosts() {
            // 投稿順 = id 昇順。期待する新着順は b2, a2, b1, a1
            a1 = insertPost(alice.getId(), "a1");
            b1 = insertPost(bob.getId(), "b1");
            a2 = insertPost(alice.getId(), "a2");
            b2 = insertPost(bob.getId(), "b2");
        }

        @Test
        @DisplayName("デシジョンテーブル: userIds=null × cursor=null → 全ユーザーの投稿を新しい順に返す")
        void allUsersFromHead() {
            List<Post> result = postMapper.findTimeline(null, null, LIMIT, alice.getId());

            assertThat(ids(result)).containsExactly(b2.getId(), a2.getId(), b1.getId(), a1.getId());
        }

        @Test
        @DisplayName("デシジョンテーブル: userIds=空 → フォロー先 0 人なので 0 件（全件にならない）")
        void emptyUserIdsReturnsNothing() {
            assertThat(postMapper.findTimeline(List.of(), null, LIMIT, alice.getId())).isEmpty();
        }

        @Test
        @DisplayName("デシジョンテーブル / ループ: userIds=1 件 → そのユーザーの投稿だけ")
        void singleUserId() {
            List<Post> result = postMapper.findTimeline(List.of(bob.getId()), null, LIMIT, alice.getId());

            assertThat(ids(result)).containsExactly(b2.getId(), b1.getId());
        }

        @Test
        @DisplayName("デシジョンテーブル / ループ: userIds=複数 → IN 句で絞り込み、存在しない id が混ざっても無視")
        void multipleUserIds() {
            List<Post> result = postMapper.findTimeline(List.of(alice.getId(), bob.getId(), 9999L), null, LIMIT, alice.getId());

            assertThat(ids(result)).containsExactly(b2.getId(), a2.getId(), b1.getId(), a1.getId());
        }

        @Test
        @DisplayName("デシジョンテーブル: cursor あり → cursor より古い（id が小さい）投稿だけ")
        void cursorReturnsOlderPostsOnly() {
            List<Post> result = postMapper.findTimeline(null, b1.getId(), LIMIT, alice.getId());

            assertThat(ids(result)).containsExactly(a1.getId());
        }

        @Test
        @DisplayName("デシジョンテーブル: userIds × cursor の両方 → 両方の条件で絞る")
        void userIdsAndCursorCombined() {
            List<Post> result = postMapper.findTimeline(List.of(alice.getId()), a2.getId(), LIMIT, alice.getId());

            assertThat(ids(result)).containsExactly(a1.getId());
        }

        @Test
        @DisplayName("境界値: cursor が最古の id なら 0 件")
        void cursorAtOldestReturnsEmpty() {
            assertThat(postMapper.findTimeline(null, a1.getId(), LIMIT, alice.getId())).isEmpty();
        }

        @Test
        @DisplayName("境界値: limit=1 は最新 1 件、limit=件数ちょうどは全件、limit=0 は 0 件")
        void limitBoundaries() {
            assertThat(ids(postMapper.findTimeline(null, null, 1, alice.getId()))).containsExactly(b2.getId());
            assertThat(postMapper.findTimeline(null, null, 4, alice.getId())).hasSize(4);
            assertThat(postMapper.findTimeline(null, null, 0, alice.getId())).isEmpty();
        }

        @Test
        @DisplayName("limit で切った後も並びは新しい順のまま（派生テーブルの外側で再ソート）")
        void limitedResultKeepsOrder() {
            List<Post> result = postMapper.findTimeline(null, null, 3, alice.getId());

            assertThat(ids(result)).containsExactly(b2.getId(), a2.getId(), b1.getId());
        }

        @Test
        @DisplayName("各行に投稿者と集計列が埋まり、likedByMe は currentUserId 基準")
        void rowsCarryAuthorAndAggregates() {
            likeMapper.insertIgnoreDuplicate(b2.getId(), alice.getId());

            List<Post> result = postMapper.findTimeline(null, null, LIMIT, alice.getId());

            Post top = result.getFirst();
            assertThat(top.getId()).isEqualTo(b2.getId());
            assertThat(top.getAuthor().getUsername()).isEqualTo("bob");
            assertThat(top.getLikeCount()).isEqualTo(1);
            assertThat(top.isLikedByMe()).isTrue();
            assertThat(result.get(1).isLikedByMe()).isFalse();
        }
    }
}
