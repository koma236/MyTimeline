package com.example.mytimeline.service;

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
import com.example.mytimeline.storage.AvatarUrlFactory;
import com.example.mytimeline.storage.ImageValidator;
import com.example.mytimeline.storage.InvalidImageException;
import com.example.mytimeline.storage.S3StorageService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 投稿の作成・取得・編集・削除とタイムライン取得の業務ロジック
 * （docs/features/F02_timeline.md / docs/features/F03_post.md）。
 */
@Service
public class PostService {

    /**
     * 更新系の操作を記録する。
     *
     * <p>本文は載せない。投稿内容はユーザーのデータであり、ログに残す必要がないため。
     * 追跡に要るのは「誰がどの投稿をどうしたか」だけ。</p>
     */
    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    /** クライアントが limit を指定しなかった場合の取得件数。 */
    static final int DEFAULT_LIMIT = 20;

    /** limit の上限（F02 6. バリデーション / 制約）。大きすぎる要求は黙ってここまで切り詰める。 */
    static final int MAX_LIMIT = 50;

    /** 1 投稿あたりの画像の上限（F03 6. バリデーション / 制約）。 */
    static final int MAX_IMAGES = 4;

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final FollowMapper followMapper;
    private final AvatarUrlFactory avatarUrlFactory;
    private final S3StorageService storageService;
    private final ImageValidator imageValidator;

    public PostService(
        PostMapper postMapper,
        PostImageMapper postImageMapper,
        FollowMapper followMapper,
        AvatarUrlFactory avatarUrlFactory,
        S3StorageService storageService,
        ImageValidator imageValidator
    ) {
        this.postMapper = postMapper;
        this.postImageMapper = postImageMapper;
        this.followMapper = followMapper;
        this.avatarUrlFactory = avatarUrlFactory;
        this.storageService = storageService;
        this.imageValidator = imageValidator;
    }

    /**
     * 投稿を作成する。画像は S3 へ保存し、DB にはキーだけを記録する（F03）。
     *
     * <p>順序は「全画像を検証 → 全画像を保存 → DB に登録」。検証を最初にまとめて行うのは、
     * 3 枚目で弾かれてから 1〜2 枚目のアップロードを取り消す羽目にならないようにするため。
     * 保存後に DB 登録が失敗した場合はトランザクションで投稿ごと巻き戻り、S3 に参照されない
     * オブジェクトが残るだけに収まる（アバター更新と同じ割り切り。F07 5. 参照）。</p>
     *
     * <p>INSERT 後に取得し直しているのは、投稿者名と DB 側で採番された日時を含んだ
     * 完全なレスポンスを返すため。クライアントはこれをそのままタイムラインの先頭に挿せる。</p>
     */
    @Transactional
    public PostResponse create(Long userId, String body, List<MultipartFile> images) {
        List<MultipartFile> files = images == null ? List.of() : images;
        if (files.size() > MAX_IMAGES) {
            throw new InvalidImageException(InvalidImageException.TOO_MANY);
        }

        String normalizedBody = body == null ? "" : body;
        if (normalizedBody.isBlank() && files.isEmpty()) {
            throw new EmptyPostException();
        }

        List<ImageValidator.ImageFormat> formats = files.stream()
            .map(imageValidator::validate)
            .toList();
        List<String> keys = uploadImages(userId, files, formats);

        Post post = new Post();
        post.setUserId(userId);
        post.setBody(normalizedBody);
        postMapper.insert(post);

        if (!keys.isEmpty()) {
            postImageMapper.insertAll(toPostImages(post.getId(), keys));
        }
        log.info("投稿を作成しました: postId={}, userId={}, images={}", post.getId(), userId, keys.size());

        return getById(post.getId(), userId);
    }

    @Transactional(readOnly = true)
    public PostResponse getById(Long id, Long currentUserId) {
        Post post = findOrThrow(id, currentUserId);
        return toResponse(post, postImageMapper.findByPostIds(List.of(id)));
    }

    /**
     * 投稿の本文を編集する。投稿者本人のみ。画像の差し替えはできない（F03）。
     *
     * <p>本文を空にできるのは画像が付いている投稿だけ。空にした結果
     * 「本文も画像も無い投稿」になるのは作成時の制約（{@link EmptyPostException}）と矛盾する。</p>
     */
    @Transactional
    public PostResponse update(Long id, Long currentUserId, PostRequest request) {
        Post post = findOrThrow(id, currentUserId);
        verifyOwner(post, currentUserId);

        String normalizedBody = request.body() == null ? "" : request.body();
        List<PostImage> images = postImageMapper.findByPostIds(List.of(id));
        if (normalizedBody.isBlank() && images.isEmpty()) {
            throw new EmptyPostException();
        }

        postMapper.updateBody(id, normalizedBody);
        log.info("投稿を編集しました: postId={}, userId={}", id, currentUserId);

        return toResponse(findOrThrow(id, currentUserId), images);
    }

    /**
     * 投稿を削除する。投稿者本人のみ。
     *
     * <p>DB 側は外部キーの ON DELETE CASCADE が配下の画像・コメント・いいねを消すため、
     * ここでは投稿だけを削除する。S3 の画像本体は DB の削除が済んでから消す。
     * 逆にすると、DB の削除が失敗したときに「行はあるのに画像の実体が無い」投稿が残る。
     * 消し損ねても参照されないオブジェクトが残るだけ（{@code deleteQuietly}）。</p>
     */
    @Transactional
    public void delete(Long id, Long currentUserId) {
        Post post = findOrThrow(id, currentUserId);
        verifyOwner(post, currentUserId);

        List<PostImage> images = postImageMapper.findByPostIds(List.of(id));
        postMapper.deleteById(id);
        log.info("投稿を削除しました: postId={}, userId={}", id, currentUserId);

        images.forEach(image -> storageService.deleteQuietly(image.getS3Key()));
    }

    /** 全体タイムライン（F02「すべて」タブ）。 */
    @Transactional(readOnly = true)
    public TimelineResponse getAllTimeline(Long currentUserId, Long cursor, Integer limit) {
        return toTimeline(null, cursor, limit, currentUserId);
    }

    /**
     * フォロー中タイムライン（F02「フォロー中」タブ）。
     *
     * <p>対象は「自分＋自分がフォローしているユーザー」。自分を含めるのは、投稿した直後に
     * それが見当たらないと投稿できたのかどうか分からないため（F02 / 04_features.md 5.2）。</p>
     *
     * <p>フォロー先の id を先に引いて {@code IN} に渡している。タイムラインの SQL に
     * {@code follows} を JOIN する手もあるが、そうすると「自分の投稿も含める」条件が
     * SQL 側に混ざる。フォロー数が数千規模になるまではこの形の方が読み解きやすい。</p>
     */
    @Transactional(readOnly = true)
    public TimelineResponse getFollowingTimeline(Long currentUserId, Long cursor, Integer limit) {
        List<Long> userIds = new ArrayList<>(followMapper.findFolloweeIds(currentUserId));
        userIds.add(currentUserId);

        return toTimeline(userIds, cursor, limit, currentUserId);
    }

    /**
     * 特定ユーザーの投稿一覧（SCR-05 プロフィール画面）。
     *
     * <p>タイムラインと同じマッパー・同じカーソル規約を使う。対象ユーザーを 1 人に
     * 絞るだけなので専用の SQL は要らず、{@code idx_posts_user_id (user_id, id DESC)} が
     * 絞り込みと並び替えの両方を賄う。</p>
     *
     * <p>対象ユーザーが存在するかの確認は呼び出し元（{@code UserService}）の責務。
     * ここで存在しない ID を渡されても 0 件が返るだけで、それでは「存在しない」と
     * 「投稿がまだ無い」を画面が区別できない。</p>
     */
    @Transactional(readOnly = true)
    public TimelineResponse getUserTimeline(Long authorId, Long currentUserId, Long cursor, Integer limit) {
        return toTimeline(List.of(authorId), cursor, limit, currentUserId);
    }

    /**
     * 1 ページ分を取得し、次ページの有無を判定する。
     *
     * <p>「次があるか」を知るために COUNT を別で流すのではなく、要求件数より 1 件多く取得して
     * 余りが出たかどうかで判断する。余った 1 件はレスポンスに含めず、返す最後の投稿の id が
     * 次のカーソルになる。</p>
     *
     * <p>いいね数・コメント数・自分のいいね状態はマッパーが同じ 1 本の SQL で埋めてくるので、
     * ここで投稿ごとに数え直すことはしない。添付画像だけは別テーブルのため、ページ分の
     * 投稿 id をまとめて 1 本で引いて突き合わせる（投稿ごとに引くと N+1 になる）。
     * ページの件数によらず発行するクエリは 2 本。</p>
     */
    private TimelineResponse toTimeline(List<Long> userIds, Long cursor, Integer limit, Long currentUserId) {
        int size = normalizeLimit(limit);
        List<Post> posts = new ArrayList<>(postMapper.findTimeline(userIds, cursor, size + 1, currentUserId));

        boolean hasNext = posts.size() > size;
        if (hasNext) {
            posts.removeLast();
        }

        Map<Long, List<PostImage>> imagesByPostId = postImageMapper
            .findByPostIds(posts.stream().map(Post::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(PostImage::getPostId));

        Long nextCursor = hasNext ? posts.getLast().getId() : null;
        return new TimelineResponse(
            posts.stream()
                .map(post -> toResponse(post, imagesByPostId.getOrDefault(post.getId(), List.of())))
                .toList(),
            nextCursor
        );
    }

    /**
     * 画像を S3 へ保存し、採番したキーを添付順で返す。
     *
     * <p>途中の 1 枚で失敗したら、保存済みの分を消してから例外にする。
     * 投稿自体が失敗するのに一部の画像だけ S3 に残っても、誰からも参照されないため。
     * （この削除はベストエフォート。消し損ねは許容する）</p>
     */
    private List<String> uploadImages(
        Long userId,
        List<MultipartFile> files,
        List<ImageValidator.ImageFormat> formats
    ) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            String key = storageService.newPostImageKey(userId, formats.get(i));
            try {
                storageService.put(key, files.get(i).getBytes(), formats.get(i).contentType());
            } catch (IOException | RuntimeException e) {
                keys.forEach(storageService::deleteQuietly);
                throw new PostImageUploadException(e);
            }
            keys.add(key);
        }
        return keys;
    }

    private List<PostImage> toPostImages(Long postId, List<String> keys) {
        List<PostImage> images = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            PostImage image = new PostImage();
            image.setPostId(postId);
            image.setS3Key(keys.get(i));
            image.setPosition(i);
            images.add(image);
        }
        return images;
    }

    /**
     * 投稿を DTO へ詰め替える。
     *
     * <p>アバター URL と画像 URL は DB の値ではなく、キーから毎回組み立てる期限付きの署名。
     * そのため {@code PostResponse.from} は解決済みの URL を引数で受け取る形になっており、
     * その解決をここで一手に引き受けている。</p>
     */
    private PostResponse toResponse(Post post, List<PostImage> images) {
        List<String> imageUrls = images.stream()
            .map(image -> storageService.presignedGetUrl(image.getS3Key()))
            .toList();
        return PostResponse.from(post, avatarUrlFactory.urlFor(post.getAuthor().getAvatarKey()), imageUrls);
    }

    /** 未指定・0 以下は既定値、上限超えは上限に丸める。 */
    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Post findOrThrow(Long id, Long currentUserId) {
        return postMapper.findById(id, currentUserId).orElseThrow(PostNotFoundException::new);
    }

    /**
     * 所有者チェック。存在確認（404）を先に済ませてから呼ぶこと。
     *
     * <p>順序を逆にすると、存在しない投稿に 403 を返してしまい
     * 「その ID の投稿が他人のものとして存在する」ことを漏らすことになる。</p>
     */
    private void verifyOwner(Post post, Long currentUserId) {
        if (!post.getUserId().equals(currentUserId)) {
            throw new PostForbiddenException();
        }
    }

    /**
     * 画像を S3 へ保存できなかった場合。
     *
     * <p>クライアントの入力ミスではなく通信・サーバー側の問題なので、
     * 個別のハンドラを用意せず {@code GlobalExceptionHandler} の 500 に流す
     * （{@code UserService.AvatarUploadException} と同じ扱い）。</p>
     */
    static class PostImageUploadException extends RuntimeException {
        PostImageUploadException(Throwable cause) {
            super("投稿画像のアップロードに失敗しました", cause);
        }
    }
}
