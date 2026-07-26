package com.example.mytimeline.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT の設定値（application.properties の {@code app.jwt.*}）。
 *
 * @param secret            HS256 の署名鍵。環境変数 JWT_SECRET から注入する。256bit（32 バイト）以上必要
 * @param expirationMinutes トークンの有効期間（分）
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMinutes) {
}
