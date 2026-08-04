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

import com.example.mytimeline.dto.CommentListResponse;
import com.example.mytimeline.dto.CommentRequest;
import com.example.mytimeline.dto.CommentResponse;
import com.example.mytimeline.exception.CommentForbiddenException;
import com.example.mytimeline.exception.CommentNotFoundException;
import com.example.mytimeline.exception.PostNotFoundException;
import com.example.mytimeline.mapper.CommentMapper;
import com.example.mytimeline.mapper.PostMapper;
import com.example.mytimeline.model.Comment;
import com.example.mytimeline.model.User;
import com.example.mytimeline.storage.AvatarUrlFactory;
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
class CommentServiceTest {

    private static final Long POST_ID = 10L;
    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private PostMapper postMapper;

    /**
     * アバター URL の組み立ては署名付き URL の発行を伴うためモックにする。
     * ここでは何も stub しないので、常に null（＝アバター未設定）として振る舞う。
     */
    @Mock
    private AvatarUrlFactory avatarUrlFactory;

    @InjectMocks
    private CommentService commentService;

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setDisplayName("表示名" + id);
        return user;
    }

    private static Comment comment(Long id, Long userId, String body) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setPostId(POST_ID);
        comment.setUserId(userId);
        comment.setBody(body);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        comment.setAuthor(user(userId));
        return comment;
    }

    /** id が 1, 2, 3... と古い順に並んだコメントを count 件作る。 */
    private static List<Comment> comments(int count) {
        return new ArrayList<>(
            IntStream.rangeClosed(1, count)
                .mapToObj(i -> comment((long) i, OWNER_ID, "コメント" + i))
                .toList()
        );
    }

    @Test
    @DisplayName("コメント一覧は古い順のまま返る")
    void listReturnsOldestFirst() {
        when(postMapper.existsById(POST_ID)).thenReturn(true);
        when(commentMapper.findByPostId(eq(POST_ID), isNull(), eq(CommentService.DEFAULT_LIMIT + 1)))
            .thenReturn(comments(3));

        CommentListResponse response = commentService.list(POST_ID, null, null);

        assertThat(response.comments()).extracting(CommentResponse::id).containsExactly(1L, 2L, 3L);
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("要求件数より多く返ったら余りを捨て、最後のコメントの id を次のカーソルにする")
    void listReturnsNextCursorWhenMoreExist() {
        when(postMapper.existsById(POST_ID)).thenReturn(true);
        // limit=2 に対し 3 件（= limit + 1）返るので次ページがある
        when(commentMapper.findByPostId(eq(POST_ID), isNull(), eq(3))).thenReturn(comments(3));

        CommentListResponse response = commentService.list(POST_ID, null, 2);

        assertThat(response.comments()).extracting(CommentResponse::id).containsExactly(1L, 2L);
        assertThat(response.nextCursor()).isEqualTo(2L);
    }

    @Test
    @DisplayName("limit が上限を超えても上限までしか取得しない")
    void listClampsLimitToMax() {
        when(postMapper.existsById(POST_ID)).thenReturn(true);
        when(commentMapper.findByPostId(eq(POST_ID), isNull(), eq(CommentService.MAX_LIMIT + 1)))
            .thenReturn(comments(1));

        commentService.list(POST_ID, null, 1000);

        verify(commentMapper).findByPostId(POST_ID, null, CommentService.MAX_LIMIT + 1);
    }

    @Test
    @DisplayName("カーソルはそのままマッパーへ渡される")
    void listPassesCursorThrough() {
        when(postMapper.existsById(POST_ID)).thenReturn(true);
        when(commentMapper.findByPostId(eq(POST_ID), eq(5L), eq(CommentService.DEFAULT_LIMIT + 1)))
            .thenReturn(comments(1));

        commentService.list(POST_ID, 5L, null);

        verify(commentMapper).findByPostId(POST_ID, 5L, CommentService.DEFAULT_LIMIT + 1);
    }

    @Test
    @DisplayName("存在しない投稿のコメント一覧は 404 相当の例外になる")
    void listThrowsWhenPostMissing() {
        when(postMapper.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.list(999L, null, null))
            .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("コメント一覧は投稿の存在確認に軽量な existsById を使う")
    void listUsesExistsInsteadOfFindById() {
        // findById は投稿者の JOIN といいね数・コメント数の集計まで走るので、
        // 存在を知りたいだけの場面で使うと無駄が大きい
        when(postMapper.existsById(POST_ID)).thenReturn(true);
        when(commentMapper.findByPostId(eq(POST_ID), isNull(), eq(CommentService.DEFAULT_LIMIT + 1)))
            .thenReturn(comments(1));

        commentService.list(POST_ID, null, null);

        verify(postMapper).existsById(POST_ID);
        verify(postMapper, never()).findById(any(), any());
    }

    @Test
    @DisplayName("コメントを作成すると採番された id で取得し直した内容を返す")
    void createReturnsPersistedComment() {
        when(postMapper.existsById(POST_ID)).thenReturn(true);
        doAnswer(invocation -> {
            invocation.getArgument(0, Comment.class).setId(7L);
            return null;
        }).when(commentMapper).insert(any(Comment.class));
        when(commentMapper.findById(7L)).thenReturn(Optional.of(comment(7L, OWNER_ID, "いい天気ですね")));

        CommentResponse response = commentService.create(POST_ID, OWNER_ID, new CommentRequest("いい天気ですね"));

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper).insert(captor.capture());
        assertThat(captor.getValue().getPostId()).isEqualTo(POST_ID);
        assertThat(captor.getValue().getUserId()).isEqualTo(OWNER_ID);
        assertThat(captor.getValue().getBody()).isEqualTo("いい天気ですね");

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.postId()).isEqualTo(POST_ID);
        assertThat(response.author().username()).isEqualTo("user1");
    }

    @Test
    @DisplayName("存在しない投稿へのコメント作成は 404 相当になり、INSERT も実行されない")
    void createThrowsWhenPostMissing() {
        when(postMapper.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.create(999L, OWNER_ID, new CommentRequest("本文")))
            .isInstanceOf(PostNotFoundException.class);

        verify(commentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("自分のコメントは編集できる")
    void updateSucceedsForOwner() {
        when(commentMapper.findById(7L))
            .thenReturn(Optional.of(comment(7L, OWNER_ID, "編集前")))
            .thenReturn(Optional.of(comment(7L, OWNER_ID, "編集後")));

        CommentResponse response = commentService.update(7L, OWNER_ID, new CommentRequest("編集後"));

        verify(commentMapper).updateBody(7L, "編集後");
        assertThat(response.body()).isEqualTo("編集後");
    }

    @Test
    @DisplayName("他人のコメントは編集できず、UPDATE も実行されない")
    void updateThrowsForNonOwner() {
        when(commentMapper.findById(7L)).thenReturn(Optional.of(comment(7L, OWNER_ID, "本文")));

        assertThatThrownBy(() -> commentService.update(7L, OTHER_USER_ID, new CommentRequest("書き換え")))
            .isInstanceOf(CommentForbiddenException.class)
            .hasMessage(CommentForbiddenException.MESSAGE);

        verify(commentMapper, never()).updateBody(any(), any());
    }

    @Test
    @DisplayName("存在しないコメントの編集は 403 ではなく 404 相当になる")
    void updateThrowsNotFoundBeforeForbidden() {
        when(commentMapper.findById(999L)).thenReturn(Optional.empty());

        // 先に 403 を返すと「その id のコメントが他人のものとして存在する」ことが漏れる
        assertThatThrownBy(() -> commentService.update(999L, OTHER_USER_ID, new CommentRequest("本文")))
            .isInstanceOf(CommentNotFoundException.class)
            .hasMessage(CommentNotFoundException.MESSAGE);
    }

    @Test
    @DisplayName("自分のコメントは削除できる")
    void deleteSucceedsForOwner() {
        when(commentMapper.findById(7L)).thenReturn(Optional.of(comment(7L, OWNER_ID, "本文")));

        commentService.delete(7L, OWNER_ID);

        verify(commentMapper).deleteById(7L);
    }

    @Test
    @DisplayName("他人のコメントは削除できず、DELETE も実行されない")
    void deleteThrowsForNonOwner() {
        when(commentMapper.findById(7L)).thenReturn(Optional.of(comment(7L, OWNER_ID, "本文")));

        assertThatThrownBy(() -> commentService.delete(7L, OTHER_USER_ID))
            .isInstanceOf(CommentForbiddenException.class);

        verify(commentMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("存在しないコメントの削除は 403 ではなく 404 相当になる")
    void deleteThrowsNotFoundBeforeForbidden() {
        when(commentMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.delete(999L, OTHER_USER_ID))
            .isInstanceOf(CommentNotFoundException.class);
    }
}
