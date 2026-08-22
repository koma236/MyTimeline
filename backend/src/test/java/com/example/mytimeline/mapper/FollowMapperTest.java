package com.example.mytimeline.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mytimeline.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * follows テーブルの SQL を H2 で検証する。
 *
 * <p>設計技法: 状態遷移（未フォロー → フォロー中 → 未フォロー。重複は冪等）、
 * エラー推測（自己フォローは CHECK 制約）。集計 3 種は 0 / 1 / 複数件で確認する。</p>
 */
class FollowMapperTest extends MapperTestBase {

    @Autowired
    private FollowMapper followMapper;

    private User alice;
    private User bob;
    private User carol;

    @BeforeEach
    void setUp() {
        alice = insertUser("alice");
        bob = insertUser("bob");
        carol = insertUser("carol");
    }

    @Test
    @DisplayName("状態遷移: 未フォロー → フォロー で 1 行挿入され exists が真になる")
    void followInsertsRow() {
        int inserted = followMapper.insertIgnoreDuplicate(alice.getId(), bob.getId());

        assertThat(inserted).isEqualTo(1);
        assertThat(followMapper.exists(alice.getId(), bob.getId())).isTrue();
    }

    @Test
    @DisplayName("状態遷移: フォロー中 → フォロー は 0 行（冪等）。行も増えない")
    void followTwiceIsIdempotent() {
        followMapper.insertIgnoreDuplicate(alice.getId(), bob.getId());

        int inserted = followMapper.insertIgnoreDuplicate(alice.getId(), bob.getId());

        assertThat(inserted).isZero();
        assertThat(followMapper.countFollowing(alice.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("状態遷移: フォロー中 → 解除 で 1 行削除され exists が偽になる")
    void unfollowDeletesRow() {
        followMapper.insertIgnoreDuplicate(alice.getId(), bob.getId());

        int deleted = followMapper.delete(alice.getId(), bob.getId());

        assertThat(deleted).isEqualTo(1);
        assertThat(followMapper.exists(alice.getId(), bob.getId())).isFalse();
    }

    @Test
    @DisplayName("状態遷移: 未フォロー → 解除 は 0 行（冪等）")
    void unfollowWithoutFollowIsIdempotent() {
        assertThat(followMapper.delete(alice.getId(), bob.getId())).isZero();
    }

    @Test
    @DisplayName("フォローは片方向。alice→bob を登録しても bob→alice は exists が偽")
    void followIsDirectional() {
        followMapper.insertIgnoreDuplicate(alice.getId(), bob.getId());

        assertThat(followMapper.exists(bob.getId(), alice.getId())).isFalse();
    }

    @Test
    @DisplayName("エラー推測: 自分自身のフォローは CHECK 制約で拒否される")
    void selfFollowIsRejectedByCheckConstraint() {
        assertThatThrownBy(() -> followMapper.insertIgnoreDuplicate(alice.getId(), alice.getId()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("集計: フォロー中数・フォロワー数は方向を区別して数える（0 / 1 / 複数）")
    void countsDistinguishDirection() {
        // alice → bob, alice → carol, carol → bob
        followMapper.insertIgnoreDuplicate(alice.getId(), bob.getId());
        followMapper.insertIgnoreDuplicate(alice.getId(), carol.getId());
        followMapper.insertIgnoreDuplicate(carol.getId(), bob.getId());

        assertThat(followMapper.countFollowing(alice.getId())).isEqualTo(2);
        assertThat(followMapper.countFollowers(alice.getId())).isZero();
        assertThat(followMapper.countFollowing(bob.getId())).isZero();
        assertThat(followMapper.countFollowers(bob.getId())).isEqualTo(2);
        assertThat(followMapper.countFollowing(carol.getId())).isEqualTo(1);
        assertThat(followMapper.countFollowers(carol.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("findFolloweeIds はフォロー先の id だけを返す（0 件 / 複数件）")
    void findFolloweeIdsReturnsOnlyFollowees() {
        assertThat(followMapper.findFolloweeIds(alice.getId())).isEmpty();

        followMapper.insertIgnoreDuplicate(alice.getId(), bob.getId());
        followMapper.insertIgnoreDuplicate(alice.getId(), carol.getId());
        followMapper.insertIgnoreDuplicate(bob.getId(), alice.getId());

        assertThat(followMapper.findFolloweeIds(alice.getId()))
            .containsExactlyInAnyOrder(bob.getId(), carol.getId());
    }
}
