package com.example.mytimeline.service;

import com.example.mytimeline.dto.PostRequest;
import com.example.mytimeline.dto.PostResponse;
import com.example.mytimeline.dto.TimelineResponse;
import com.example.mytimeline.exception.PostForbiddenException;
import com.example.mytimeline.exception.PostNotFoundException;
import com.example.mytimeline.mapper.PostMapper;
import com.example.mytimeline.model.Post;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final PostMapper postMapper;

    public PostService(PostMapper postMapper) {
        this.postMapper = postMapper;
    }

    /**
     * 投稿を作成する。
     *
     * <p>INSERT 後に取得し直しているのは、投稿者名と DB 側で採番された日時を含んだ
     * 完全なレスポンスを返すため。クライアントはこれをそのままタイムラインの先頭に挿せる。</p>
     */
    @Transactional
    public PostResponse create(Long userId, PostRequest request) {
        Post post = new Post();
        post.setUserId(userId);
        post.setBody(request.body());
        postMapper.insert(post);
        log.info("投稿を作成しました: postId={}, userId={}", post.getId(), userId);

        return PostResponse.from(findOrThrow(post.getId()));
    }

    @Transactional(readOnly = true)
    public PostResponse getById(Long id) {
        return PostResponse.from(findOrThrow(id));
    }

    /**
     * 投稿の本文を編集する。投稿者本人のみ。
     */
    @Transactional
    public PostResponse update(Long id, Long currentUserId, PostRequest request) {
        Post post = findOrThrow(id);
        verifyOwner(post, currentUserId);

        postMapper.updateBody(id, request.body());
        log.info("投稿を編集しました: postId={}, userId={}", id, currentUserId);

        return PostResponse.from(findOrThrow(id));
    }

    /**
     * 投稿を削除する。投稿者本人のみ。
     *
     * <p>配下の画像・コメント・いいねは外部キーの ON DELETE CASCADE で
     * DB が消すため、ここでは投稿だけを削除する。</p>
     */
    @Transactional
    public void delete(Long id, Long currentUserId) {
        Post post = findOrThrow(id);
        verifyOwner(post, currentUserId);

        postMapper.deleteById(id);
        log.info("投稿を削除しました: postId={}, userId={}", id, currentUserId);
    }

    /** 全体タイムライン（F02「すべて」タブ）。 */
    @Transactional(readOnly = true)
    public TimelineResponse getAllTimeline(Long cursor, Integer limit) {
        return toTimeline(null, cursor, limit);
    }

    /**
     * フォロー中タイムライン（F02「フォロー中」タブ）。
     *
     * <p>本来は「自分＋自分がフォローしているユーザー」が対象だが、follows テーブルは
     * F06（フォロー機能）で作るため、現状は自分の投稿だけを返す。F06 実装時に
     * follows から取得した ID を足すだけで済むよう、マッパーは ID のリストを受ける形にしてある。</p>
     */
    @Transactional(readOnly = true)
    public TimelineResponse getFollowingTimeline(Long currentUserId, Long cursor, Integer limit) {
        return toTimeline(List.of(currentUserId), cursor, limit);
    }

    /**
     * 1 ページ分を取得し、次ページの有無を判定する。
     *
     * <p>「次があるか」を知るために COUNT を別で流すのではなく、要求件数より 1 件多く取得して
     * 余りが出たかどうかで判断する。余った 1 件はレスポンスに含めず、返す最後の投稿の id が
     * 次のカーソルになる。</p>
     */
    private TimelineResponse toTimeline(List<Long> userIds, Long cursor, Integer limit) {
        int size = normalizeLimit(limit);
        List<Post> posts = new ArrayList<>(postMapper.findTimeline(userIds, cursor, size + 1));

        boolean hasNext = posts.size() > size;
        if (hasNext) {
            posts.removeLast();
        }

        Long nextCursor = hasNext ? posts.getLast().getId() : null;
        return new TimelineResponse(posts.stream().map(PostResponse::from).toList(), nextCursor);
    }

    /** 未指定・0 以下は既定値、上限超えは上限に丸める。 */
    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Post findOrThrow(Long id) {
        return postMapper.findById(id).orElseThrow(PostNotFoundException::new);
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
}
