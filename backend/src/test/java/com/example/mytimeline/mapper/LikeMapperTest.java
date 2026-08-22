package com.example.mytimeline.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mytimeline.model.Post;
import com.example.mytimeline.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * likes テーブルの SQL を H2 で検証する。
 *
 * <p>設計技法: 状態遷移（未いいね → いいね済 → 未いいね。済→済 / 未→未 は冪等）。
 * 二重いいねの防止はアプリではなく UNIQUE 制約 + ON CONFLICT が担うので、
 * その挙動は DB を通さないと確認できない。</p>
 */
class LikeMapperTest extends MapperTestBase {

    @Autowired
    private LikeMapper likeMapper;

    private User alice;
    private User bob;
    private Post post;

    @BeforeEach
    void setUp() {
        alice = insertUser("alice");
        bob = insertUser("bob");
        post = insertPost(alice.getId(), "本文");
    }

    @Test
    @DisplayName("状態遷移: 未いいね → いいね で 1 行挿入され、件数が 1 になる")
    void likeInsertsRow() {
        int inserted = likeMapper.insertIgnoreDuplicate(post.getId(), bob.getId());

        assertThat(inserted).isEqualTo(1);
        assertThat(likeMapper.countByPostId(post.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("状態遷移: いいね済 → いいね は ON CONFLICT で 0 行（冪等）。件数も増えない")
    void likeTwiceIsIdempotent() {
        likeMapper.insertIgnoreDuplicate(post.getId(), bob.getId());

        int inserted = likeMapper.insertIgnoreDuplicate(post.getId(), bob.getId());

        assertThat(inserted).isZero();
        assertThat(likeMapper.countByPostId(post.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("状態遷移: いいね済 → 取り消し で 1 行削除され、件数が 0 に戻る")
    void unlikeDeletesRow() {
        likeMapper.insertIgnoreDuplicate(post.getId(), bob.getId());

        int deleted = likeMapper.delete(post.getId(), bob.getId());

        assertThat(deleted).isEqualTo(1);
        assertThat(likeMapper.countByPostId(post.getId())).isZero();
    }

    @Test
    @DisplayName("状態遷移: 未いいね → 取り消し は 0 行（冪等）")
    void unlikeWithoutLikeIsIdempotent() {
        int deleted = likeMapper.delete(post.getId(), bob.getId());

        assertThat(deleted).isZero();
    }

    @Test
    @DisplayName("件数は投稿ごとに数える（別ユーザー複数 = 複数件、別投稿は混ざらない）")
    void countIsPerPost() {
        Post other = insertPost(bob.getId(), "別の投稿");
        likeMapper.insertIgnoreDuplicate(post.getId(), alice.getId());
        likeMapper.insertIgnoreDuplicate(post.getId(), bob.getId());
        likeMapper.insertIgnoreDuplicate(other.getId(), alice.getId());

        assertThat(likeMapper.countByPostId(post.getId())).isEqualTo(2);
        assertThat(likeMapper.countByPostId(other.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("いいねの無い投稿の件数は 0")
    void countOfUnlikedPostIsZero() {
        assertThat(likeMapper.countByPostId(post.getId())).isZero();
    }
}
