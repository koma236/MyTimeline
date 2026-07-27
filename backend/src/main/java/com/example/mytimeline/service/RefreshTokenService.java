package com.example.mytimeline.service;

import com.example.mytimeline.exception.InvalidRefreshTokenException;
import com.example.mytimeline.mapper.RefreshTokenMapper;
import com.example.mytimeline.model.RefreshToken;
import com.example.mytimeline.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * リフレッシュトークンの発行・検証・失効。
 *
 * <p>アクセストークン（JWT）と違い、リフレッシュトークンは<b>署名付きトークンではなく
 * 単なるランダム値</b>である。ログアウトで失効させられることが要件なので、
 * どのみち DB を引く必要があり、署名で自己完結させる意味がないため。</p>
 *
 * <p>DB に保存するのは生値の SHA-256 ハッシュのみ。パスワードと同じく、
 * DB が漏れてもそのままセッションを乗っ取られないようにする。
 * ソルトを使わないのは、生値が 256bit の乱数で総当たりも辞書攻撃も成立しないため。</p>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /** 生値のバイト長。256bit あれば衝突・推測とも現実的に起こらない。 */
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenMapper refreshTokenMapper;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration expiration;

    public RefreshTokenService(
        RefreshTokenMapper refreshTokenMapper,
        RefreshTokenRevoker refreshTokenRevoker,
        JwtProperties properties
    ) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.refreshTokenRevoker = refreshTokenRevoker;
        this.expiration = Duration.ofDays(properties.refreshExpirationDays());
    }

    /**
     * 新しいリフレッシュトークンを発行し、生値を返す。
     *
     * <p>生値を返すのはこのメソッドだけで、以降どこにも保存されない
     * （DB にはハッシュのみ）。呼び出し元は Cookie に載せてすぐ手放すこと。</p>
     */
    @Transactional
    public String issue(Long userId) {
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(hash(rawToken));
        entity.setExpiresAt(LocalDateTime.now().plus(expiration));
        refreshTokenMapper.insert(entity);

        return rawToken;
    }

    /**
     * 提示されたトークンを検証し、ローテーションして新しい生値を返す。
     *
     * <p>失効済みトークンの再提示は「盗まれた古いトークンが使われた」可能性が高い。
     * 正規の利用者は必ず最新のトークンを持っているため、古い物が出てきた時点で
     * どちらが正規か判別できない。そのため<b>そのユーザーの全セッションを失効させる</b>。</p>
     *
     * @return 新しいリフレッシュトークンの生値と、対象ユーザー ID
     */
    @Transactional
    public RotationResult rotate(String rawToken) {
        RefreshToken current = refreshTokenMapper.findByTokenHash(hash(rawToken))
            .orElseThrow(InvalidRefreshTokenException::new);

        LocalDateTime now = LocalDateTime.now();

        if (current.isRevoked()) {
            log.warn("失効済みリフレッシュトークンが再提示されたため、ユーザー {} の全セッションを失効させます",
                current.getUserId());
            // 直後に投げる例外でこのトランザクションはロールバックされるため、
            // 失効は独立したトランザクションでコミットさせる必要がある
            refreshTokenRevoker.revokeAllByUserId(current.getUserId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpiredAt(now)) {
            throw new InvalidRefreshTokenException();
        }

        refreshTokenMapper.revokeById(current.getId(), now);
        return new RotationResult(current.getUserId(), issue(current.getUserId()));
    }

    /**
     * ログアウト。提示されたトークンが属するユーザーの全セッションを失効させる。
     *
     * <p>トークンが無効でも例外にしない。ログアウトは「結果としてログアウト状態になる」
     * ことが目的で、失敗を伝える意味がないため。</p>
     */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenMapper.findByTokenHash(hash(rawToken))
            .ifPresent(token -> refreshTokenMapper.revokeAllByUserId(token.getUserId(), LocalDateTime.now()));
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 は JRE 必須アルゴリズムなので到達しない
            throw new IllegalStateException("SHA-256 が利用できません", e);
        }
    }

    /**
     * @param userId      ローテーション対象のユーザー
     * @param rawToken    新しいリフレッシュトークンの生値
     */
    public record RotationResult(Long userId, String rawToken) {
    }
}
