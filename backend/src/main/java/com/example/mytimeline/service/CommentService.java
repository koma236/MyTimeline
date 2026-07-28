package com.example.mytimeline.service;

import com.example.mytimeline.dto.CommentListResponse;
import com.example.mytimeline.dto.CommentRequest;
import com.example.mytimeline.dto.CommentResponse;
import com.example.mytimeline.exception.CommentForbiddenException;
import com.example.mytimeline.exception.CommentNotFoundException;
import com.example.mytimeline.exception.PostNotFoundException;
import com.example.mytimeline.mapper.CommentMapper;
import com.example.mytimeline.mapper.PostMapper;
import com.example.mytimeline.model.Comment;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * コメントの一覧・作成・編集・削除（docs/features/F04_comment.md）。
 */
@Service
public class CommentService {

    /**
     * 更新系の操作を記録する。
     *
     * <p>{@link PostService} と同じく本文は載せない。コメント内容はユーザーのデータであり、
     * 追跡に要るのは「誰がどのコメントをどうしたか」だけ。</p>
     */
    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    /** クライアントが limit を指定しなかった場合の取得件数。 */
    static final int DEFAULT_LIMIT = 20;

    /** limit の上限。大きすぎる要求は黙ってここまで切り詰める。 */
    static final int MAX_LIMIT = 50;

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;

    public CommentService(CommentMapper commentMapper, PostMapper postMapper) {
        this.commentMapper = commentMapper;
        this.postMapper = postMapper;
    }

    /**
     * 投稿のコメントを古い順に 1 ページ分取得する。
     *
     * <p>投稿者は SQL の JOIN で埋まるため、コメントの件数によらずクエリは 1 本。</p>
     */
    @Transactional(readOnly = true)
    public CommentListResponse list(Long postId, Long cursor, Integer limit) {
        verifyPostExists(postId);

        int size = normalizeLimit(limit);
        // 「次があるか」を COUNT で別に問い合わせず、1 件多く取って余りが出たかで判断する
        List<Comment> comments = new ArrayList<>(commentMapper.findByPostId(postId, cursor, size + 1));

        boolean hasNext = comments.size() > size;
        if (hasNext) {
            comments.removeLast();
        }

        Long nextCursor = hasNext ? comments.getLast().getId() : null;
        return new CommentListResponse(
            comments.stream().map(CommentResponse::from).toList(),
            nextCursor
        );
    }

    /**
     * コメントを作成する。
     *
     * <p>INSERT 後に取得し直しているのは、投稿者名と DB 側で採番された日時を含んだ
     * 完全なレスポンスを返すため。クライアントはこれをそのまま一覧の末尾に足せる。</p>
     */
    @Transactional
    public CommentResponse create(Long postId, Long currentUserId, CommentRequest request) {
        verifyPostExists(postId);

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(currentUserId);
        comment.setBody(request.body());
        commentMapper.insert(comment);
        log.info("コメントを作成しました: commentId={}, postId={}, userId={}",
            comment.getId(), postId, currentUserId);

        return CommentResponse.from(findOrThrow(comment.getId()));
    }

    /**
     * コメントの本文を編集する。コメント投稿者本人のみ。
     */
    @Transactional
    public CommentResponse update(Long id, Long currentUserId, CommentRequest request) {
        Comment comment = findOrThrow(id);
        verifyOwner(comment, currentUserId);

        commentMapper.updateBody(id, request.body());
        log.info("コメントを編集しました: commentId={}, userId={}", id, currentUserId);

        return CommentResponse.from(findOrThrow(id));
    }

    /**
     * コメントを削除する。コメント投稿者本人のみ。
     *
     * <p>投稿者（コメント先の投稿の持ち主）は他人のコメントを消せない。X と同じく、
     * 削除できるのは自分が書いたものだけという扱いにしている。</p>
     */
    @Transactional
    public void delete(Long id, Long currentUserId) {
        Comment comment = findOrThrow(id);
        verifyOwner(comment, currentUserId);

        commentMapper.deleteById(id);
        log.info("コメントを削除しました: commentId={}, userId={}", id, currentUserId);
    }

    /** 未指定・0 以下は既定値、上限超えは上限に丸める。 */
    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * コメント先の投稿が存在することを確かめる。
     *
     * <p>存在確認だけなので {@code findById} ではなく {@code existsById} を使う。
     * 前者は投稿者の JOIN といいね数・コメント数の集計まで走り、ここでは全部無駄になる。</p>
     */
    private void verifyPostExists(Long postId) {
        if (!postMapper.existsById(postId)) {
            throw new PostNotFoundException();
        }
    }

    private Comment findOrThrow(Long id) {
        return commentMapper.findById(id).orElseThrow(CommentNotFoundException::new);
    }

    /**
     * 所有者チェック。存在確認（404）を先に済ませてから呼ぶこと。
     *
     * <p>{@link PostService} の所有者チェックと同じ理由で順序が重要。逆にすると、存在しない
     * コメントに 403 を返してしまい「その ID のコメントが他人のものとして存在する」ことを
     * 漏らすことになる。</p>
     */
    private void verifyOwner(Comment comment, Long currentUserId) {
        if (!comment.getUserId().equals(currentUserId)) {
            throw new CommentForbiddenException();
        }
    }
}
