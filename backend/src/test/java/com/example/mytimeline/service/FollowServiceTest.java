package com.example.mytimeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mytimeline.dto.FollowResponse;
import com.example.mytimeline.exception.ProfileNotFoundException;
import com.example.mytimeline.exception.SelfFollowException;
import com.example.mytimeline.mapper.FollowMapper;
import com.example.mytimeline.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    private static final Long CURRENT_USER_ID = 1L;
    private static final Long TARGET_ID = 2L;

    @Mock
    private FollowMapper followMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private FollowService followService;

    @Test
    @DisplayName("フォローするとフォロワー数とフォロー済みの状態を返す")
    void followReturnsCountAndState() {
        when(userMapper.existsById(TARGET_ID)).thenReturn(true);
        when(followMapper.insertIgnoreDuplicate(CURRENT_USER_ID, TARGET_ID)).thenReturn(1);
        when(followMapper.countFollowers(TARGET_ID)).thenReturn(3L);

        FollowResponse response = followService.follow(TARGET_ID, CURRENT_USER_ID);

        assertThat(response.followerCount()).isEqualTo(3L);
        assertThat(response.followingByMe()).isTrue();
    }

    @Test
    @DisplayName("すでにフォロー済みでも例外にはならず、フォロー済みのまま件数を返す（冪等）")
    void followIsIdempotent() {
        when(userMapper.existsById(TARGET_ID)).thenReturn(true);
        // ON CONFLICT DO NOTHING なので 0 件挿入。二重フォローは DB の UNIQUE が弾く
        when(followMapper.insertIgnoreDuplicate(CURRENT_USER_ID, TARGET_ID)).thenReturn(0);
        when(followMapper.countFollowers(TARGET_ID)).thenReturn(3L);

        FollowResponse response = followService.follow(TARGET_ID, CURRENT_USER_ID);

        assertThat(response.followerCount()).isEqualTo(3L);
        assertThat(response.followingByMe()).isTrue();
    }

    @Test
    @DisplayName("フォローを解除するとフォロワー数と未フォローの状態を返す")
    void unfollowReturnsCountAndState() {
        when(userMapper.existsById(TARGET_ID)).thenReturn(true);
        when(followMapper.delete(CURRENT_USER_ID, TARGET_ID)).thenReturn(1);
        when(followMapper.countFollowers(TARGET_ID)).thenReturn(2L);

        FollowResponse response = followService.unfollow(TARGET_ID, CURRENT_USER_ID);

        assertThat(response.followerCount()).isEqualTo(2L);
        assertThat(response.followingByMe()).isFalse();
    }

    @Test
    @DisplayName("フォローしていない相手の解除も例外にはならない（冪等）")
    void unfollowIsIdempotent() {
        when(userMapper.existsById(TARGET_ID)).thenReturn(true);
        when(followMapper.delete(CURRENT_USER_ID, TARGET_ID)).thenReturn(0);
        when(followMapper.countFollowers(TARGET_ID)).thenReturn(2L);

        FollowResponse response = followService.unfollow(TARGET_ID, CURRENT_USER_ID);

        assertThat(response.followerCount()).isEqualTo(2L);
        assertThat(response.followingByMe()).isFalse();
    }

    @Test
    @DisplayName("自分自身のフォローは 400 相当になり、INSERT も実行されない")
    void followRejectsSelf() {
        assertThatThrownBy(() -> followService.follow(CURRENT_USER_ID, CURRENT_USER_ID))
            .isInstanceOf(SelfFollowException.class)
            .hasMessage(SelfFollowException.MESSAGE);

        verify(followMapper, never()).insertIgnoreDuplicate(any(), any());
        // 存在確認より先に弾く。自分が存在するのは自明なので問い合わせるだけ無駄
        verify(userMapper, never()).existsById(any());
    }

    @Test
    @DisplayName("存在しないユーザーへのフォローは 404 相当になり、INSERT も実行されない")
    void followThrowsWhenTargetMissing() {
        when(userMapper.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> followService.follow(999L, CURRENT_USER_ID))
            .isInstanceOf(ProfileNotFoundException.class)
            .hasMessage(ProfileNotFoundException.MESSAGE);

        verify(followMapper, never()).insertIgnoreDuplicate(any(), any());
    }

    @Test
    @DisplayName("存在しないユーザーのフォロー解除は 404 相当になり、DELETE も実行されない")
    void unfollowThrowsWhenTargetMissing() {
        when(userMapper.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> followService.unfollow(999L, CURRENT_USER_ID))
            .isInstanceOf(ProfileNotFoundException.class);

        verify(followMapper, never()).delete(any(), any());
    }

    @Test
    @DisplayName("フォロー先の存在確認には軽量な existsById を使う")
    void usesExistsInsteadOfFindById() {
        // findById は password_hash まで含む行を丸ごと読むので、存在を知りたいだけなら重い
        when(userMapper.existsById(TARGET_ID)).thenReturn(true);
        when(followMapper.insertIgnoreDuplicate(CURRENT_USER_ID, TARGET_ID)).thenReturn(1);
        when(followMapper.countFollowers(TARGET_ID)).thenReturn(1L);

        followService.follow(TARGET_ID, CURRENT_USER_ID);

        verify(userMapper).existsById(TARGET_ID);
        verify(userMapper, never()).findById(any());
    }
}
