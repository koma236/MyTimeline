package com.example.mytimeline.service;

import com.example.mytimeline.dto.FollowResponse;
import com.example.mytimeline.exception.ProfileNotFoundException;
import com.example.mytimeline.exception.SelfFollowException;
import com.example.mytimeline.mapper.FollowMapper;
import com.example.mytimeline.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * フォローの登録・解除（docs/features/F06_follow.md）。
 *
 * <p>{@link LikeService} と同じく、どちらの操作も冪等。すでにフォロー済みで再度フォローしても、
 * フォローしていない相手を解除してもエラーにはせず、現在の状態を返す（F06 7. 異常系）。
 * ボタン連打や通信の再送で状態が壊れないようにするため。</p>
 */
@Service
public class FollowService {

    private static final Logger log = LoggerFactory.getLogger(FollowService.class);

    private final FollowMapper followMapper;
    private final UserMapper userMapper;

    public FollowService(FollowMapper followMapper, UserMapper userMapper) {
        this.followMapper = followMapper;
        this.userMapper = userMapper;
    }

    /**
     * フォローする。すでにフォロー済みならフォロワー数だけ返す。
     */
    @Transactional
    public FollowResponse follow(Long followeeId, Long currentUserId) {
        if (followeeId.equals(currentUserId)) {
            throw new SelfFollowException();
        }
        verifyUserExists(followeeId);

        if (followMapper.insertIgnoreDuplicate(currentUserId, followeeId) > 0) {
            log.info("フォローしました: followerId={}, followeeId={}", currentUserId, followeeId);
        }

        return new FollowResponse(followMapper.countFollowers(followeeId), true);
    }

    /**
     * フォローを解除する。フォローしていなければフォロワー数だけ返す。
     *
     * <p>フォローと違い、自分自身を指定されても例外にしない。自己フォローは存在し得ないので
     * 解除しても 0 件更新で終わり、「フォローしていない相手の解除」と同じ冪等な結果になる。
     * ここで弾いても防げる誤りは無く、分岐が増えるだけ。</p>
     */
    @Transactional
    public FollowResponse unfollow(Long followeeId, Long currentUserId) {
        verifyUserExists(followeeId);

        if (followMapper.delete(currentUserId, followeeId) > 0) {
            log.info("フォローを解除しました: followerId={}, followeeId={}", currentUserId, followeeId);
        }

        return new FollowResponse(followMapper.countFollowers(followeeId), false);
    }

    /**
     * フォロー先が存在することを確かめる（F06 7. 存在しないユーザー：404）。
     *
     * <p>外部キー制約でも弾けるが、それだと 500 になってしまうためアプリ側で先に見る。
     * 存在確認だけなので行を丸ごと読む {@code findById} ではなく {@code existsById} を使う。</p>
     */
    private void verifyUserExists(Long userId) {
        if (!userMapper.existsById(userId)) {
            throw new ProfileNotFoundException();
        }
    }
}
