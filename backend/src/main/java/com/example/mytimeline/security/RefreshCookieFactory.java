package com.example.mytimeline.security;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * リフレッシュトークン Cookie の組み立て。属性をここ 1 箇所に集約する。
 *
 * <p>httpOnly にすることで JavaScript から読めなくなり、XSS があっても
 * リフレッシュトークンだけは持ち出されない。アクセストークンは
 * ヘッダで扱う都合上 JS から触れるが、15 分で失効するため被害が限定される。</p>
 */
@Component
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "refreshToken";

    /**
     * 認証エンドポイント以外には送らせない。投稿 API などに毎回付いて回るのを防ぐ。
     */
    private static final String COOKIE_PATH = "/api/auth";

    private final Duration maxAge;
    private final boolean secure;

    public RefreshCookieFactory(JwtProperties properties) {
        this.maxAge = Duration.ofDays(properties.refreshExpirationDays());
        this.secure = properties.refreshCookieSecure();
    }

    public ResponseCookie create(String rawToken) {
        return baseBuilder(rawToken)
            .maxAge(maxAge)
            .build();
    }

    /**
     * ブラウザに Cookie を削除させるための空 Cookie。
     * 属性（Path など）が発行時と一致していないと削除されない点に注意。
     */
    public ResponseCookie expired() {
        return baseBuilder("")
            .maxAge(0)
            .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(secure)
            // フロントとバックエンドは同一オリジン（開発は Vite プロキシ、本番は CloudFront）
            // で動くため Lax で足りる。None にすると Secure が必須になる
            .sameSite("Lax")
            .path(COOKIE_PATH);
    }
}
