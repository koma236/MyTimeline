package com.example.mytimeline.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mytimeline.model.Comment;
import com.example.mytimeline.model.Post;
import com.example.mytimeline.model.User;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * comments テーブルの SQL を H2 で検証する。
 *
 * <p>設計技法: 境界値（limit・cursor）、分岐網羅（{@code <if cursor>}）、エラー推測（別投稿のコメントが混ざらない）。
 * タイムライン（新しい順・cursor は {@code id <}）と逆に、コメントは古い順・cursor は {@code id >} なので、
 * 方向を取り違えていないことを重点的に見る。</p>
 */
class CommentMapperTest extends MapperTestBase {

    private static final int LIMIT = 10;

    @Autowired
    private CommentMapper commentMapper;

    private User alice;
    private User bob;
    private Post post;

    @BeforeEach
    void setUp() {
        alice = insertUser("alice");
        bob = insertUser("bob");
        post = insertPost(alice.getId(), "本文");
    }

    private Comment insertComment(Long postId, Long userId, String body) {
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setBody(body);
        commentMapper.insert(comment);
        return comment;
    }

    private static List<Long> ids(List<Comment> comments) {
        return comments.stream().map(Comment::getId).toList();
    }

    @Test
    @DisplayName("insert で id が採番され、findById で投稿者（author）と日時が埋まる")
    void insertAndFindById() {
        Comment comment = insertComment(post.getId(), bob.getId(), "いいね");

        assertThat(comment.getId()).isNotNull();
        Comment found = commentMapper.findById(comment.getId()).orElseThrow();
        assertThat(found.getPostId()).isEqualTo(post.getId());
        assertThat(found.getUserId()).isEqualTo(bob.getId());
        assertThat(found.getBody()).isEqualTo("いいね");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getAuthor().getId()).isEqualTo(bob.getId());
        assertThat(found.getAuthor().getUsername()).isEqualTo("bob");
        assertThat(found.getAuthor().getPasswordHash()).isNull();
    }

    @Test
    @DisplayName("findById は存在しなければ empty")
    void findByIdReturnsEmptyWhenMissing() {
        assertThat(commentMapper.findById(9999L)).isEmpty();
    }

    @Test
    @DisplayName("findByPostId は古い順（id 昇順）で返し、別投稿のコメントは混ざらない")
    void findByPostIdOrdersAscendingAndIsolatesPost() {
        Comment c1 = insertComment(post.getId(), alice.getId(), "1");
        Comment c2 = insertComment(post.getId(), bob.getId(), "2");
        Post other = insertPost(bob.getId(), "別の投稿");
        insertComment(other.getId(), alice.getId(), "other");
        Comment c3 = insertComment(post.getId(), alice.getId(), "3");

        List<Comment> result = commentMapper.findByPostId(post.getId(), null, LIMIT);

        assertThat(ids(result)).containsExactly(c1.getId(), c2.getId(), c3.getId());
        assertThat(result).allSatisfy(comment -> assertThat(comment.getAuthor()).isNotNull());
    }

    @Test
    @DisplayName("境界値: コメントが無い投稿は空リスト")
    void findByPostIdReturnsEmptyWhenNoComments() {
        assertThat(commentMapper.findByPostId(post.getId(), null, LIMIT)).isEmpty();
    }

    @Test
    @DisplayName("分岐: cursor ありは cursor より新しい（id が大きい）コメントだけを返す")
    void cursorReturnsNewerCommentsOnly() {
        Comment c1 = insertComment(post.getId(), alice.getId(), "1");
        Comment c2 = insertComment(post.getId(), bob.getId(), "2");
        Comment c3 = insertComment(post.getId(), alice.getId(), "3");

        List<Comment> result = commentMapper.findByPostId(post.getId(), c1.getId(), LIMIT);

        assertThat(ids(result)).containsExactly(c2.getId(), c3.getId());
    }

    @Test
    @DisplayName("境界値: cursor が最新の id なら 0 件")
    void cursorAtNewestReturnsEmpty() {
        insertComment(post.getId(), alice.getId(), "1");
        Comment last = insertComment(post.getId(), bob.getId(), "2");

        assertThat(commentMapper.findByPostId(post.getId(), last.getId(), LIMIT)).isEmpty();
    }

    @Test
    @DisplayName("境界値: limit=1 は最古の 1 件、limit=件数ちょうどは全件、limit+1 件あれば limit 件")
    void limitBoundaries() {
        Comment c1 = insertComment(post.getId(), alice.getId(), "1");
        insertComment(post.getId(), bob.getId(), "2");
        insertComment(post.getId(), alice.getId(), "3");

        assertThat(ids(commentMapper.findByPostId(post.getId(), null, 1))).containsExactly(c1.getId());
        assertThat(commentMapper.findByPostId(post.getId(), null, 3)).hasSize(3);
        assertThat(commentMapper.findByPostId(post.getId(), null, 2)).hasSize(2);
    }

    @Test
    @DisplayName("updateBody は本文を更新し updated_at を進める。存在しない id は 0 行")
    void updateBody() {
        Comment comment = insertComment(post.getId(), bob.getId(), "before");
        LocalDateTime past = LocalDateTime.of(2000, 1, 1, 0, 0);
        jdbc.update("UPDATE comments SET updated_at = ? WHERE id = ?", past, comment.getId());

        assertThat(commentMapper.updateBody(comment.getId(), "after")).isEqualTo(1);
        Comment found = commentMapper.findById(comment.getId()).orElseThrow();
        assertThat(found.getBody()).isEqualTo("after");
        assertThat(found.getUpdatedAt()).isAfter(past);

        assertThat(commentMapper.updateBody(9999L, "x")).isZero();
    }

    @Test
    @DisplayName("deleteById は 1 行削除し、再度呼ぶと 0 行（冪等）")
    void deleteById() {
        Comment comment = insertComment(post.getId(), bob.getId(), "x");

        assertThat(commentMapper.deleteById(comment.getId())).isEqualTo(1);
        assertThat(commentMapper.findById(comment.getId())).isEmpty();
        assertThat(commentMapper.deleteById(comment.getId())).isZero();
    }
}
