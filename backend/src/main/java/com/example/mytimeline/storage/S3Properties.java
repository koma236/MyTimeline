package com.example.mytimeline.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * オブジェクトストレージの設定値（application.properties の {@code app.s3.*}）。
 *
 * <p>ローカルは MinIO、本番は AWS S3 を使う。どちらも S3 API 互換なので、
 * コードは同じまま環境変数だけで切り替わる（docs/09_infrastructure.md）。</p>
 *
 * @param endpoint       アプリからの接続先。空なら {@code endpointOverride} を付けない（＝本物の S3）
 * @param publicEndpoint 署名付き URL に載せるホスト。**ブラウザから到達できる必要がある**。
 *                       SigV4 は Host ヘッダを署名対象に含むため、生成後に URL のホストを
 *                       書き換えると署名が壊れる。そのため内部用（{@code endpoint}）と
 *                       公開用を別々に持ち、presigner だけ公開用で組み立てる。
 *                       Docker Compose ではアプリから {@code http://minio:9000}、
 *                       ブラウザから {@code http://localhost:9000} と到達先が異なる
 * @param bucket         画像を置くバケット名
 * @param region         署名に使うリージョン。MinIO でも何らかの値が必要
 * @param accessKey      アクセスキー。空なら {@code DefaultCredentialsProvider}（本番の IAM ロール）を使う
 * @param secretKey      シークレットキー
 * @param pathStyleAccess {@code true} で {@code endpoint/bucket/key} 形式にする。
 *                       MinIO では必須（仮想ホスト形式だと {@code bucket.localhost} になり名前解決できない）
 * @param avatarUrlExpirationMinutes アバターの署名付き URL の有効期間（分）
 */
@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(
    String endpoint,
    String publicEndpoint,
    String bucket,
    String region,
    String accessKey,
    String secretKey,
    boolean pathStyleAccess,
    long avatarUrlExpirationMinutes
) {
}
