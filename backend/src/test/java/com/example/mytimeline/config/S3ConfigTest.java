package com.example.mytimeline.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mytimeline.storage.S3Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 クライアントの組み立てを検証する。
 *
 * <p>設計技法: デシジョンテーブル（accessKey の有無 × endpoint の有無）。
 * ローカル（MinIO: 静的キー + endpoint 指定）と本番（IAM ロール: キー無し + endpoint 無し）の
 * 2 つの構成がどちらも Bean を作れることを見る。認証情報の解決は遅延されるため、
 * 本物の AWS 資格情報が無くても build() は通る。</p>
 */
class S3ConfigTest {

    private static S3Properties properties(String endpoint, String publicEndpoint, String accessKey) {
        return new S3Properties(endpoint, publicEndpoint, "bucket", "ap-northeast-1", accessKey, "secret", true, 60);
    }

    @Test
    @DisplayName("デシジョンテーブル: 静的キー + endpoint あり（ローカル MinIO 構成）で S3Client を作れる")
    void buildsClientWithStaticCredentialsAndEndpoint() {
        try (S3Client client = new S3Config().s3Client(properties("http://localhost:9000", "http://localhost:9000", "key"))) {
            assertThat(client).isNotNull();
            assertThat(client.serviceClientConfiguration().endpointOverride()).isPresent();
        }
    }

    @Test
    @DisplayName("デシジョンテーブル: キー無し + endpoint 無し（本番 IAM ロール構成）でも S3Client を作れる")
    void buildsClientWithDefaultCredentialsAndNoEndpoint() {
        try (S3Client client = new S3Config().s3Client(properties("", "", ""))) {
            assertThat(client).isNotNull();
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
        }
    }

    @Test
    @DisplayName("Presigner は publicEndpoint の有無の両方で作れる（署名対象の Host が変わる）")
    void buildsPresignerWithAndWithoutPublicEndpoint() {
        try (S3Presigner withEndpoint = new S3Config().s3Presigner(properties("http://minio:9000", "http://localhost:9000", "key"));
             S3Presigner withoutEndpoint = new S3Config().s3Presigner(properties("", "", ""))) {
            assertThat(withEndpoint).isNotNull();
            assertThat(withoutEndpoint).isNotNull();
        }
    }
}
