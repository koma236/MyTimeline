package com.example.mytimeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mytimeline.dto.PostRequest;
import com.example.mytimeline.dto.PostResponse;
import com.example.mytimeline.dto.TimelineResponse;
import com.example.mytimeline.exception.PostForbiddenException;
import com.example.mytimeline.exception.PostNotFoundException;
import com.example.mytimeline.mapper.PostMapper;
import com.example.mytimeline.model.Post;
import com.example.mytimeline.model.User;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private PostMapper postMapper;

    @InjectMocks
    private PostService postService;

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName("表示名" + id);
        return user;
    }

    private static Post post(Long id, Long userId, String body) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(userId);
        post.setBody(body);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        post.setAuthor(user(userId, "user" + userId));
        return post;
    }

    /** id が 100, 99, 98... と新しい順に並んだ投稿を count 件作る。 */
    private static List<Post> posts(int count) {
        return new ArrayList<>(
            IntStream.range(0, count)
                .mapToObj(i -> post(100L - i, OWNER_ID, "本文" + i))
                .toList()
        );
    }

    @Test
    @DisplayName("投稿を作成すると採番された id で取得し直した内容を返す")
    void createReturnsPersistedPost() {
        // insert は採番された id をモデルに書き戻す。その振る舞いをモックで再現する
        doAnswer(invocation -> {
            invocation.getArgument(0, Post.class).setId(10L);
            return null;
        }).when(postMapper).insert(any(Post.class));
        when(postMapper.findById(10L, OWNER_ID)).thenReturn(Optional.of(post(10L, OWNER_ID, "こんにちは")));

        PostResponse response = postService.create(OWNER_ID, new PostRequest("こんにちは"));

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(OWNER_ID);
        assertThat(captor.getValue().getBody()).isEqualTo("こんにちは");

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.body()).isEqualTo("こんにちは");
        assertThat(response.author().username()).isEqualTo("user1");
    }

    @Test
    @DisplayName("存在しない投稿の取得は 404 相当の例外になる")
    void getByIdThrowsWhenMissing() {
        when(postMapper.findById(999L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getById(999L, OWNER_ID))
            .isInstanceOf(PostNotFoundException.class)
            .hasMessage(PostNotFoundException.MESSAGE);
    }

    @Test
    @DisplayName("いいね数・コメント数・自分のいいね状態はレスポンスにそのまま載る")
    void getByIdCarriesAggregatesIntoResponse() {
        // 数え直さず、マッパーが 1 本の SQL で埋めてきた値をそのまま返す（N+1 回避）
        Post post = post(10L, OWNER_ID, "本文");
        post.setLikeCount(3L);
        post.setCommentCount(2L);
        post.setLikedByMe(true);
        when(postMapper.findById(10L, OTHER_USER_ID)).thenReturn(Optional.of(post));

        PostResponse response = postService.getById(10L, OTHER_USER_ID);

        assertThat(response.likeCount()).isEqualTo(3L);
        assertThat(response.commentCount()).isEqualTo(2L);
        assertThat(response.likedByMe()).isTrue();
    }

    @Test
    @DisplayName("自分の投稿は編集できる")
    void updateSucceedsForOwner() {
        when(postMapper.findById(10L, OWNER_ID))
            .thenReturn(Optional.of(post(10L, OWNER_ID, "編集前")))
            .thenReturn(Optional.of(post(10L, OWNER_ID, "編集後")));

        PostResponse response = postService.update(10L, OWNER_ID, new PostRequest("編集後"));

        verify(postMapper).updateBody(10L, "編集後");
        assertThat(response.body()).isEqualTo("編集後");
    }

    @Test
    @DisplayName("他人の投稿は編集できず、UPDATE も実行されない")
    void updateThrowsForNonOwner() {
        when(postMapper.findById(10L, OTHER_USER_ID)).thenReturn(Optional.of(post(10L, OWNER_ID, "本文")));

        assertThatThrownBy(() -> postService.update(10L, OTHER_USER_ID, new PostRequest("書き換え")))
            .isInstanceOf(PostForbiddenException.class)
            .hasMessage(PostForbiddenException.MESSAGE);

        verify(postMapper, never()).updateBody(any(), any());
    }

    @Test
    @DisplayName("存在しない投稿の編集は 403 ではなく 404 相当になる")
    void updateThrowsNotFoundBeforeForbidden() {
        when(postMapper.findById(999L, OTHER_USER_ID)).thenReturn(Optional.empty());

        // 先に 403 を返すと「その id の投稿が他人のものとして存在する」ことが漏れる
        assertThatThrownBy(() -> postService.update(999L, OTHER_USER_ID, new PostRequest("本文")))
            .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("自分の投稿は削除できる")
    void deleteSucceedsForOwner() {
        when(postMapper.findById(10L, OWNER_ID)).thenReturn(Optional.of(post(10L, OWNER_ID, "本文")));

        postService.delete(10L, OWNER_ID);

        verify(postMapper).deleteById(10L);
    }

    @Test
    @DisplayName("他人の投稿は削除できず、DELETE も実行されない")
    void deleteThrowsForNonOwner() {
        when(postMapper.findById(10L, OTHER_USER_ID)).thenReturn(Optional.of(post(10L, OWNER_ID, "本文")));

        assertThatThrownBy(() -> postService.delete(10L, OTHER_USER_ID))
            .isInstanceOf(PostForbiddenException.class);

        verify(postMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("limit 未指定なら既定件数で取得する")
    void timelineUsesDefaultLimit() {
        when(postMapper.findTimeline(isNull(), isNull(), eq(PostService.DEFAULT_LIMIT + 1), eq(OWNER_ID)))
            .thenReturn(posts(3));

        TimelineResponse response = postService.getAllTimeline(OWNER_ID, null, null);

        assertThat(response.posts()).hasSize(3);
    }

    @Test
    @DisplayName("limit が上限を超えても上限までしか取得しない")
    void timelineClampsLimitToMax() {
        when(postMapper.findTimeline(isNull(), isNull(), eq(PostService.MAX_LIMIT + 1), eq(OWNER_ID)))
            .thenReturn(posts(5));

        postService.getAllTimeline(OWNER_ID, null, 1000);

        verify(postMapper).findTimeline(null, null, PostService.MAX_LIMIT + 1, OWNER_ID);
    }

    @Test
    @DisplayName("limit が 0 以下なら既定件数に戻す")
    void timelineFallsBackToDefaultForNonPositiveLimit() {
        when(postMapper.findTimeline(isNull(), isNull(), eq(PostService.DEFAULT_LIMIT + 1), eq(OWNER_ID)))
            .thenReturn(posts(1));

        postService.getAllTimeline(OWNER_ID, null, 0);

        verify(postMapper).findTimeline(null, null, PostService.DEFAULT_LIMIT + 1, OWNER_ID);
    }

    @Test
    @DisplayName("要求件数より多く返ったら余りを捨て、最後の投稿の id を次のカーソルにする")
    void timelineReturnsNextCursorWhenMoreExist() {
        // limit=2 に対し 3 件（= limit + 1）返るので次ページがある
        when(postMapper.findTimeline(isNull(), isNull(), eq(3), eq(OWNER_ID))).thenReturn(posts(3));

        TimelineResponse response = postService.getAllTimeline(OWNER_ID, null, 2);

        assertThat(response.posts()).hasSize(2);
        assertThat(response.posts()).extracting(PostResponse::id).containsExactly(100L, 99L);
        assertThat(response.nextCursor()).isEqualTo(99L);
    }

    @Test
    @DisplayName("要求件数以下しか返らなければ次のカーソルは null になる")
    void timelineReturnsNullCursorWhenNoMore() {
        when(postMapper.findTimeline(isNull(), isNull(), eq(3), eq(OWNER_ID))).thenReturn(posts(2));

        TimelineResponse response = postService.getAllTimeline(OWNER_ID, null, 2);

        assertThat(response.posts()).hasSize(2);
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("0 件でも例外にならず空のタイムラインを返す")
    void timelineReturnsEmpty() {
        when(postMapper.findTimeline(isNull(), isNull(), eq(3), eq(OWNER_ID))).thenReturn(List.of());

        TimelineResponse response = postService.getAllTimeline(OWNER_ID, null, 2);

        assertThat(response.posts()).isEmpty();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("フォロー中タイムラインは現状ログインユーザー自身のみを対象にする")
    void followingTimelineTargetsSelfOnly() {
        // follows テーブルは F06 で追加する。それまでは自分の投稿だけが対象
        when(postMapper.findTimeline(
            eq(List.of(OWNER_ID)), isNull(), eq(PostService.DEFAULT_LIMIT + 1), eq(OWNER_ID)))
            .thenReturn(posts(1));

        postService.getFollowingTimeline(OWNER_ID, null, null);

        verify(postMapper).findTimeline(List.of(OWNER_ID), null, PostService.DEFAULT_LIMIT + 1, OWNER_ID);
    }

    @Test
    @DisplayName("カーソルはそのままマッパーへ渡される")
    void timelinePassesCursorThrough() {
        when(postMapper.findTimeline(isNull(), eq(50L), eq(PostService.DEFAULT_LIMIT + 1), eq(OWNER_ID)))
            .thenReturn(posts(1));

        postService.getAllTimeline(OWNER_ID, 50L, null);

        verify(postMapper).findTimeline(null, 50L, PostService.DEFAULT_LIMIT + 1, OWNER_ID);
    }

    @Test
    @DisplayName("タイムラインの件数によらずマッパーの呼び出しは 1 回だけ（N+1 を作らない）")
    void timelineIssuesSingleMapperCall() {
        // 投稿ごとに件数を数え直す実装になっていないことの歯止め。
        // いいね数・コメント数・自分のいいね状態は findTimeline が同じ SQL で埋めてくる
        when(postMapper.findTimeline(isNull(), isNull(), eq(PostService.DEFAULT_LIMIT + 1), eq(OWNER_ID)))
            .thenReturn(posts(20));

        postService.getAllTimeline(OWNER_ID, null, null);

        verify(postMapper).findTimeline(null, null, PostService.DEFAULT_LIMIT + 1, OWNER_ID);
        verify(postMapper, never()).findById(any(), any());
    }
}
