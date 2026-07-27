package com.example.mytimeline.service;

import com.example.mytimeline.mapper.RefreshTokenMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ユーザーの全セッションを、呼び出し元とは独立したトランザクションで失効させる。
 *
 * <p>盗用検知は「全セッションを失効させたうえで例外を投げる」処理である。
 * 呼び出し元と同じトランザクションで実行すると、その例外によってロールバックされ、
 * 失効がなかったことになってしまう。そのため {@link Propagation#REQUIRES_NEW} で
 * 独立したトランザクションを開始し、例外とは無関係にコミットさせる。</p>
 *
 * <p>Spring の {@code @Transactional} はプロキシ経由でしか効かず、同一クラス内の
 * メソッド呼び出しでは伝播設定が無視される。そのため
 * {@link RefreshTokenService} とは別のクラスに切り出している。</p>
 */
@Service
public class RefreshTokenRevoker {

    private final RefreshTokenMapper refreshTokenMapper;

    public RefreshTokenRevoker(RefreshTokenMapper refreshTokenMapper) {
        this.refreshTokenMapper = refreshTokenMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllByUserId(Long userId, LocalDateTime revokedAt) {
        refreshTokenMapper.revokeAllByUserId(userId, revokedAt);
    }
}
