package com.example.mytimeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mytimeline.dto.LikeResponse;
import com.example.mytimeline.exception.PostNotFoundException;
import com.example.mytimeline.mapper.LikeMapper;
import com.example.mytimeline.mapper.PostMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    private static final Long POST_ID = 10L;
    private static final Long USER_ID = 1L;

    @Mock
    private LikeMapper likeMapper;

    @Mock
    private PostMapper postMapper;

    @InjectMocks
    private LikeService likeService;

    @Test
    @DisplayName("いいねを付けると件数といいね済みの状態を返す")
    void likeReturnsCountAndState() {
        when(postMapper.existsById(POST_ID)).thenReturn(true);
        when(likeMapper.insertIgnoreDuplicate(POST_ID, USER_ID)).thenReturn(1);
        when(likeMapper.countByPostId(POST_ID)).thenReturn(3L);

        LikeResponse response = likeService.like(POST_ID, USER_ID);

        assertThat(response.likeCount()).isEqualTo(3L);
        assertThat(response.likedByMe()).isTrue();
    }

    @Test
    @DisplayName("すでにいいね済みでも例外にはならず、いいね済みのまま件数を返す（冪等）")
    void likeIsIdempotent() {
        when(postMapper.existsById(POST_ID)).thenReturn(true);
        // ON CONFLICT DO NOTHING なので 0 件挿入。二重いいねは DB の UNIQUE が弾く
        when(likeMapper.insertIgnoreDuplicate(POST_ID, USER_ID)).thenReturn(0);
        when(likeMapper.countByPostId(POST_ID)).thenReturn(3L);

        LikeResponse response = likeService.like(POST_ID, USER_ID);

        assertThat(response.likeCount()).isEqualTo(3L);
        assertThat(response.likedByMe()).isTrue();
    }

    @Test
    @DisplayName("いいねを取り消すと件数と未いいねの状態を返す")
    void unlikeReturnsCountAndState() {
        when(postMapper.existsById(POST_ID)).thenReturn(true);
        when(likeMapper.delete(POST_ID, USER_ID)).thenReturn(1);
        when(likeMapper.countByPostId(POST_ID)).thenReturn(2L);

        LikeResponse response = likeService.unlike(POST_ID, USER_ID);

        assertThat(response.likeCount()).isEqualTo(2L);
        assertThat(response.likedByMe()).isFalse();
    }

    @Test
    @DisplayName("いいねしていない投稿の取り消しも例外にはならない（冪等）")
    void unlikeIsIdempotent() {
        when(postMapper.existsById(POST_ID)).thenReturn(true);
        when(likeMapper.delete(POST_ID, USER_ID)).thenReturn(0);
        when(likeMapper.countByPostId(POST_ID)).thenReturn(2L);

        LikeResponse response = likeService.unlike(POST_ID, USER_ID);

        assertThat(response.likeCount()).isEqualTo(2L);
        assertThat(response.likedByMe()).isFalse();
    }

    @Test
    @DisplayName("存在しない投稿へのいいねは 404 相当になり、INSERT も実行されない")
    void likeThrowsWhenPostMissing() {
        when(postMapper.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> likeService.like(999L, USER_ID))
            .isInstanceOf(PostNotFoundException.class)
            .hasMessage(PostNotFoundException.MESSAGE);

        verify(likeMapper, never()).insertIgnoreDuplicate(any(), any());
    }

    @Test
    @DisplayName("存在しない投稿のいいね取り消しは 404 相当になり、DELETE も実行されない")
    void unlikeThrowsWhenPostMissing() {
        when(postMapper.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> likeService.unlike(999L, USER_ID))
            .isInstanceOf(PostNotFoundException.class);

        verify(likeMapper, never()).delete(any(), any());
    }

    @Test
    @DisplayName("投稿の存在確認には軽量な existsById を使う")
    void usesExistsInsteadOfFindById() {
        // findById は投稿者の JOIN といいね数・コメント数の集計まで走るので、
        // 存在を知りたいだけの場面で使うと無駄が大きい
        when(postMapper.existsById(POST_ID)).thenReturn(true);
        when(likeMapper.insertIgnoreDuplicate(POST_ID, USER_ID)).thenReturn(1);
        when(likeMapper.countByPostId(POST_ID)).thenReturn(1L);

        likeService.like(POST_ID, USER_ID);

        verify(postMapper).existsById(POST_ID);
        verify(postMapper, never()).findById(any(), any());
    }
}
