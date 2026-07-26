package com.example.mytimeline.security;

import com.example.mytimeline.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * JWT の発行と検証。JWT ライブラリへの依存はこのクラスに閉じる。
 *
 * <p>トークンに入れるのは「誰か」を特定するための情報だけで、
 * パスワードやメールアドレスなどの秘密情報は含めない（ペイロードは誰でも復号できるため）。</p>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String CLAIM_USERNAME = "username";

    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        // 署名アルゴリズムは鍵長から決まる（32/48/64 バイト以上でそれぞれ HS256 / HS384 / HS512）。
        // 32 バイト未満の場合は WeakKeyException となり起動時に気付ける。
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(properties.expirationMinutes());
    }

    /**
     * ユーザーに対する署名済みトークンを発行する。
     */
    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(String.valueOf(user.getId()))
            .claim(CLAIM_USERNAME, user.getUsername())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(expiration)))
            .signWith(signingKey)
            .compact();
    }

    /**
     * トークンの署名と有効期限を検証し、認証済みユーザーを復元する。
     *
     * <p>署名不正・期限切れ・形式不正はいずれも「認証できなかった」として
     * {@link Optional#empty()} を返す。呼び出し側で理由を区別する必要はない。</p>
     */
    public Optional<CurrentUser> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            return Optional.of(new CurrentUser(userId, claims.get(CLAIM_USERNAME, String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT の検証に失敗しました: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
