package com.example.mytimeline.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS の設定値（application.properties の {@code app.cors.*}）。
 *
 * <p>許可オリジンをコードに埋め込まず環境変数で差し替えられるようにしている。
 * 埋め込んでしまうと開発用の {@code http://localhost:*} が本番でも許可され、
 * 手元のページから認証情報付き（{@code allowCredentials=true}）で API を
 * 叩けてしまうため。</p>
 *
 * @param allowedOriginPatterns 許可するオリジンのパターン。ワイルドカードを含められる
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
    List<String> allowedOriginPatterns
) {
}
