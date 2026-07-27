package com.example.mytimeline.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * トークンの設定値（application.properties の {@code app.jwt.*}）。
 *
 * @param secret                 HS256 の署名鍵。環境変数 JWT_SECRET から注入する。256bit（32 バイト）以上必要
 * @param accessExpirationMinutes アクセストークン（JWT）の有効期間（分）
 * @param refreshExpirationDays  リフレッシュトークンの有効期間（日）
 * @param refreshCookieSecure    リフレッシュトークン Cookie に Secure 属性を付けるか。
 *                               HTTPS でない開発環境で true にすると Cookie が送信されなくなる
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret,
    long accessExpirationMinutes,
    long refreshExpirationDays,
    boolean refreshCookieSecure
) {
}
