package com.example.mytimeline.service;

import com.example.mytimeline.dto.LikeResponse;
import com.example.mytimeline.exception.PostNotFoundException;
import com.example.mytimeline.mapper.LikeMapper;
import com.example.mytimeline.mapper.PostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * いいねの付与・取り消し（docs/features/F05_like.md）。
 *
 * <p>どちらの操作も冪等。すでにいいね済みで再度付与しても、いいねしていない状態で
 * 取り消しても、エラーにはせず現在の状態を返す（F05 7. 異常系）。ボタン連打や
 * 通信の再送で状態が壊れないようにするため。</p>
 *
 * <p>自分の投稿へのいいねは制限しない（F05 6.）。X も同じ挙動。</p>
 */
@Service
public class LikeService {

    private static final Logger log = LoggerFactory.getLogger(LikeService.class);

    private final LikeMapper likeMapper;
    private final PostMapper postMapper;

    public LikeService(LikeMapper likeMapper, PostMapper postMapper) {
        this.likeMapper = likeMapper;
        this.postMapper = postMapper;
    }

    /** いいねを付ける。すでに付いていれば件数だけ返す。 */
    @Transactional
    public LikeResponse like(Long postId, Long currentUserId) {
        verifyPostExists(postId);

        if (likeMapper.insertIgnoreDuplicate(postId, currentUserId) > 0) {
            log.info("いいねを付けました: postId={}, userId={}", postId, currentUserId);
        }

        return new LikeResponse(likeMapper.countByPostId(postId), true);
    }

    /** いいねを取り消す。付いていなければ件数だけ返す。 */
    @Transactional
    public LikeResponse unlike(Long postId, Long currentUserId) {
        verifyPostExists(postId);

        if (likeMapper.delete(postId, currentUserId) > 0) {
            log.info("いいねを取り消しました: postId={}, userId={}", postId, currentUserId);
        }

        return new LikeResponse(likeMapper.countByPostId(postId), false);
    }

    /**
     * いいね先の投稿が存在することを確かめる（F05 7. 存在しない投稿：404）。
     *
     * <p>存在確認だけなので {@code findById} ではなく {@code existsById} を使う。
     * 前者は投稿者の JOIN といいね数・コメント数の集計まで走り、ここでは全部無駄になる。</p>
     */
    private void verifyPostExists(Long postId) {
        if (!postMapper.existsById(postId)) {
            throw new PostNotFoundException();
        }
    }
}
