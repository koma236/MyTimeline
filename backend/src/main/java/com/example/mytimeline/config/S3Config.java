package com.example.mytimeline.config;

import com.example.mytimeline.storage.S3Properties;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * オブジェクトストレージ（S3 / MinIO）のクライアント定義。
 *
 * <p>組み立てを {@code S3StorageService} のコンストラクタではなくここに置いているのは、
 * {@link URI#create} が不正な値で例外を投げうるため。コンストラクタが例外を投げるクラスは
 * SpotBugs の CT_CONSTRUCTOR_THROW 対象になり、回避するには final 化が要る
 * （{@code JwtService} がその形）。組み立てを {@code @Bean} メソッドへ寄せれば、
 * サービス側は完成したクライアントを受け取るだけで済む。</p>
 *
 * <p>{@code S3Client} / {@code S3Presigner} はどちらも {@code AutoCloseable} なので、
 * Spring が destroyMethod を推論してシャットダウン時に閉じる。明示は不要。</p>
 */
@Configuration
public class S3Config {

    /**
     * 実際に PUT / DELETE を行うクライアント。アプリからの到達先（{@code app.s3.endpoint}）を使う。
     */
    @Bean
    public S3Client s3Client(S3Properties properties) {
        S3ClientBuilderSupport support = new S3ClientBuilderSupport(properties);
        var builder = S3Client.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(support.credentialsProvider())
            .serviceConfiguration(support.serviceConfiguration());

        if (StringUtils.hasText(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    /**
     * 署名付き URL を発行するクライアント。**ブラウザから到達できるホスト**
     * （{@code app.s3.public-endpoint}）で組み立てる。
     *
     * <p>SigV4 は Host ヘッダを署名に含むため、内部ホストで署名した URL の
     * ホスト名を後から差し替えると署名が一致せず 403 になる。だから
     * {@code s3Client} とはエンドポイントを分けている。</p>
     */
    @Bean
    public S3Presigner s3Presigner(S3Properties properties) {
        S3ClientBuilderSupport support = new S3ClientBuilderSupport(properties);
        var builder = S3Presigner.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(support.credentialsProvider())
            .serviceConfiguration(support.serviceConfiguration());

        if (StringUtils.hasText(properties.publicEndpoint())) {
            builder.endpointOverride(URI.create(properties.publicEndpoint()));
        }
        return builder.build();
    }

    /**
     * 2 つのクライアントで共通する設定の組み立て。
     */
    private record S3ClientBuilderSupport(S3Properties properties) {

        /**
         * アクセスキーが設定されていれば静的な認証情報、空なら既定のチェーンを使う。
         *
         * <p>ローカルの MinIO はキーを直接渡すしかないが、本番の EC2 では
         * IAM ロールから取得させたい。キーを空にするだけで後者に切り替わる。</p>
         */
        AwsCredentialsProvider credentialsProvider() {
            if (StringUtils.hasText(properties.accessKey())) {
                return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
                );
            }
            return DefaultCredentialsProvider.builder().build();
        }

        S3Configuration serviceConfiguration() {
            return S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyleAccess())
                .build();
        }
    }
}
