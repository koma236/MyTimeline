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
import com.example.mytimeline.exception.EmptyPostException;
import com.example.mytimeline.exception.PostForbiddenException;
import com.example.mytimeline.exception.PostNotFoundException;
import com.example.mytimeline.mapper.FollowMapper;
import com.example.mytimeline.mapper.PostImageMapper;
import com.example.mytimeline.mapper.PostMapper;
import com.example.mytimeline.model.Post;
import com.example.mytimeline.model.PostImage;
import com.example.mytimeline.model.User;
import com.example.mytimeline.storage.AvatarUrlFactory;
import com.example.mytimeline.storage.ImageValidator;
import com.example.mytimeline.storage.InvalidImageException;
import com.example.mytimeline.storage.S3StorageService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private PostMapper postMapper;

    @Mock
    private PostImageMapper postImageMapper;

    @Mock
    private FollowMapper followMapper;

    /**
     * アバター URL の組み立ては署名付き URL の発行を伴うためモックにする。
     * ここでは何も stub しないので、常に null（＝アバター未設定）として振る舞う。
     */
    @Mock
    private AvatarUrlFactory avatarUrlFactory;

    @Mock
    private S3StorageService storageService;

    @Mock
    private ImageValidator imageValidator;

    @InjectMocks
    private PostService postService;

    private static MultipartFile file(String name) {
        return new MockMultipartFile("images", name, "image/jpeg", new byte[] {1, 2, 3});
    }

    private static PostImage postImage(Long postId, String key, int position) {
        PostImage image = new PostImage();
        image.setPostId(postId);
        image.setS3Key(key);
        image.setPosition(position);
        return image;
    }

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

        PostResponse response = postService.create(OWNER_ID, "こんにちは", List.of());

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(OWNER_ID);
        assertThat(captor.getValue().getBody()).isEqualTo("こんにちは");

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.body()).isEqualTo("こんにちは");
        assertThat(response.author().username()).isEqualTo("user1");

        // 画像なしの投稿では post_images に INSERT しない（VALUES が空の不正な SQL になる）
        verify(postImageMapper, never()).insertAll(any());
    }

    @Test
    @DisplayName("画像付きの投稿は S3 へ保存し、キーを添付順の position で登録する")
    void createUploadsImagesAndPersistsKeys() {
        when(imageValidator.validate(any())).thenReturn(ImageValidator.ImageFormat.JPEG);
        when(storageService.newPostImageKey(OWNER_ID, ImageValidator.ImageFormat.JPEG))
            .thenReturn("posts/1/key1.jpg", "posts/1/key2.jpg");
        doAnswer(invocation -> {
            invocation.getArgument(0, Post.class).setId(10L);
            return null;
        }).when(postMapper).insert(any(Post.class));
        when(postMapper.findById(10L, OWNER_ID)).thenReturn(Optional.of(post(10L, OWNER_ID, "画像付き")));
        when(postImageMapper.findByPostIds(List.of(10L))).thenReturn(List.of(
            postImage(10L, "posts/1/key1.jpg", 0),
            postImage(10L, "posts/1/key2.jpg", 1)
        ));
        when(storageService.presignedGetUrl("posts/1/key1.jpg")).thenReturn("https://s3.example/1");
        when(storageService.presignedGetUrl("posts/1/key2.jpg")).thenReturn("https://s3.example/2");

        PostResponse response = postService.create(OWNER_ID, "画像付き", List.of(file("a.jpg"), file("b.jpg")));

        verify(storageService).put(eq("posts/1/key1.jpg"), any(), eq("image/jpeg"));
        verify(storageService).put(eq("posts/1/key2.jpg"), any(), eq("image/jpeg"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PostImage>> captor = ArgumentCaptor.forClass(List.class);
        verify(postImageMapper).insertAll(captor.capture());
        assertThat(captor.getValue()).extracting(PostImage::getS3Key)
            .containsExactly("posts/1/key1.jpg", "posts/1/key2.jpg");
        assertThat(captor.getValue()).extracting(PostImage::getPosition).containsExactly(0, 1);

        // レスポンスの画像 URL は表示順（position 順）
        assertThat(response.imageUrls()).containsExactly("https://s3.example/1", "https://s3.example/2");
    }

    @Test
    @DisplayName("本文が空でも画像があれば投稿できる")
    void createAllowsBlankBodyWithImages() {
        when(imageValidator.validate(any())).thenReturn(ImageValidator.ImageFormat.PNG);
        when(storageService.newPostImageKey(OWNER_ID, ImageValidator.ImageFormat.PNG))
            .thenReturn("posts/1/key1.png");
        doAnswer(invocation -> {
            invocation.getArgument(0, Post.class).setId(10L);
            return null;
        }).when(postMapper).insert(any(Post.class));
        when(postMapper.findById(10L, OWNER_ID)).thenReturn(Optional.of(post(10L, OWNER_ID, "")));

        postService.create(OWNER_ID, "", List.of(file("a.png")));

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).insert(captor.capture());
        assertThat(captor.getValue().getBody()).isEmpty();
    }

    @Test
    @DisplayName("本文が空で画像も無い投稿は拒否され、何も保存されない")
    void createRejectsEmptyPost() {
        assertThatThrownBy(() -> postService.create(OWNER_ID, "   ", List.of()))
            .isInstanceOf(EmptyPostException.class);

        verify(postMapper, never()).insert(any());
        verify(storageService, never()).put(any(), any(), any());
    }

    @Test
    @DisplayName("画像が 4 枚を超える投稿は拒否され、何もアップロードされない")
    void createRejectsTooManyImages() {
        List<MultipartFile> files = Collections.nCopies(PostService.MAX_IMAGES + 1, file("a.jpg"));

        assertThatThrownBy(() -> postService.create(OWNER_ID, "本文", files))
            .isInstanceOf(InvalidImageException.class)
            .hasMessage(InvalidImageException.TOO_MANY);

        verify(storageService, never()).put(any(), any(), any());
        verify(postMapper, never()).insert(any());
    }

    @Test
    @DisplayName("途中の画像の保存に失敗したら保存済みの分を消し、投稿も作らない")
    void createCleansUpUploadedImagesOnFailure() {
        when(imageValidator.validate(any())).thenReturn(ImageValidator.ImageFormat.JPEG);
        when(storageService.newPostImageKey(OWNER_ID, ImageValidator.ImageFormat.JPEG))
            .thenReturn("posts/1/key1.jpg", "posts/1/key2.jpg");
        doAnswer(invocation -> null)
            .doThrow(new RuntimeException("S3 down"))
            .when(storageService).put(any(), any(), any());

        assertThatThrownBy(() -> postService.create(OWNER_ID, "本文", List.of(file("a.jpg"), file("b.jpg"))))
            .hasMessageContaining("投稿画像のアップロードに失敗しました");

        // 1 枚目は保存済みなので後始末する。投稿自体は作られない
        verify(storageService).deleteQuietly("posts/1/key1.jpg");
        verify(postMapper, never()).insert(any());
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
    @DisplayName("画像付きの投稿は本文を空にできる")
    void updateAllowsBlankBodyWithImages() {
        when(postMapper.findById(10L, OWNER_ID))
            .thenReturn(Optional.of(post(10L, OWNER_ID, "本文あり")));
        when(postImageMapper.findByPostIds(List.of(10L)))
            .thenReturn(List.of(postImage(10L, "posts/1/key1.jpg", 0)));

        postService.update(10L, OWNER_ID, new PostRequest(""));

        verify(postMapper).updateBody(10L, "");
    }

    @Test
    @DisplayName("画像の無い投稿の本文は空にできない")
    void updateRejectsBlankBodyWithoutImages() {
        // 空にできてしまうと「本文も画像も無い投稿」になり、作成時の制約と矛盾する
        when(postMapper.findById(10L, OWNER_ID)).thenReturn(Optional.of(post(10L, OWNER_ID, "本文あり")));
        when(postImageMapper.findByPostIds(List.of(10L))).thenReturn(List.of());

        assertThatThrownBy(() -> postService.update(10L, OWNER_ID, new PostRequest("   ")))
            .isInstanceOf(EmptyPostException.class);

        verify(postMapper, never()).updateBody(any(), any());
    }

    @Test
    @DisplayName("自分の投稿は削除できる")
    void deleteSucceedsForOwner() {
        when(postMapper.findById(10L, OWNER_ID)).thenReturn(Optional.of(post(10L, OWNER_ID, "本文")));

        postService.delete(10L, OWNER_ID);

        verify(postMapper).deleteById(10L);
    }

    @Test
    @DisplayName("投稿の削除は DB の削除が済んでから S3 の画像を消す")
    void deleteRemovesStorageObjectsAfterDbDelete() {
        // 逆順だと DB の削除が失敗したときに「行はあるのに画像の実体が無い」投稿が残る
        when(postMapper.findById(10L, OWNER_ID)).thenReturn(Optional.of(post(10L, OWNER_ID, "本文")));
        when(postImageMapper.findByPostIds(List.of(10L))).thenReturn(List.of(
            postImage(10L, "posts/1/key1.jpg", 0),
            postImage(10L, "posts/1/key2.jpg", 1)
        ));

        postService.delete(10L, OWNER_ID);

        InOrder inOrder = Mockito.inOrder(postMapper, storageService);
        inOrder.verify(postMapper).deleteById(10L);
        inOrder.verify(storageService).deleteQuietly("posts/1/key1.jpg");
        inOrder.verify(storageService).deleteQuietly("posts/1/key2.jpg");
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
    @DisplayName("フォロー中タイムラインはフォロー先とログインユーザー自身を対象にする")
    void followingTimelineTargetsFolloweesAndSelf() {
        when(followMapper.findFolloweeIds(OWNER_ID)).thenReturn(List.of(OTHER_USER_ID, 3L));
        when(postMapper.findTimeline(any(), isNull(), eq(PostService.DEFAULT_LIMIT + 1), eq(OWNER_ID)))
            .thenReturn(posts(1));

        postService.getFollowingTimeline(OWNER_ID, null, null);

        // 自分の投稿も含める。投稿した直後にそれが見当たらないと投稿できたのか分からないため
        verify(postMapper).findTimeline(
            List.of(OTHER_USER_ID, 3L, OWNER_ID), null, PostService.DEFAULT_LIMIT + 1, OWNER_ID);
    }

    @Test
    @DisplayName("誰もフォローしていなければフォロー中タイムラインは自分の投稿だけになる")
    void followingTimelineFallsBackToSelf() {
        when(followMapper.findFolloweeIds(OWNER_ID)).thenReturn(List.of());
        when(postMapper.findTimeline(any(), isNull(), eq(PostService.DEFAULT_LIMIT + 1), eq(OWNER_ID)))
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

    @Test
    @DisplayName("タイムラインの画像はページ分の投稿 id をまとめて 1 回で引き、各投稿に振り分ける")
    void timelineFetchesImagesInSingleQuery() {
        when(postMapper.findTimeline(isNull(), isNull(), eq(PostService.DEFAULT_LIMIT + 1), eq(OWNER_ID)))
            .thenReturn(posts(3));
        // id 100, 99, 98 のうち 99 だけに画像が付いている
        when(postImageMapper.findByPostIds(List.of(100L, 99L, 98L)))
            .thenReturn(List.of(postImage(99L, "posts/1/key1.jpg", 0)));
        when(storageService.presignedGetUrl("posts/1/key1.jpg")).thenReturn("https://s3.example/1");

        TimelineResponse response = postService.getAllTimeline(OWNER_ID, null, null);

        verify(postImageMapper).findByPostIds(List.of(100L, 99L, 98L));
        assertThat(response.posts().get(0).imageUrls()).isEmpty();
        assertThat(response.posts().get(1).imageUrls()).containsExactly("https://s3.example/1");
        assertThat(response.posts().get(2).imageUrls()).isEmpty();
    }

    /**
     * 設計技法: 境界値分析 + 条件網羅（{@code limit == null || limit <= 0}）。
     * Mapper には「次があるか」判定のため常に +1 した件数が渡る。
     */
    @ParameterizedTest(name = "境界値: limit={0} → Mapper には {1}+1 件を要求する")
    @CsvSource(nullValues = "null", value = {
        "null, 20",
        "-1, 20",
        "0, 20",
        "1, 1",
        "20, 20",
        "50, 50",
        "51, 50",
    })
    void timelineLimitBoundaries(Integer limit, int effective) {
        when(postMapper.findTimeline(isNull(), isNull(), eq(effective + 1), eq(OWNER_ID))).thenReturn(posts(1));

        postService.getAllTimeline(OWNER_ID, null, limit);

        verify(postMapper).findTimeline(null, null, effective + 1, OWNER_ID);
    }
}
