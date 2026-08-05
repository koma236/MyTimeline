package com.example.mytimeline.storage;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * オブジェクトストレージへの保存・削除・署名付き URL 発行。
 * AWS SDK への依存はこのクラスに閉じる（{@code JwtService} が JWT ライブラリに対して行っているのと同じ）。
 *
 * <p>DB には画像本体を持たず、ここで採番したキーだけを保存する（CLAUDE.md 注意事項）。</p>
 */
@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private static final String AVATAR_KEY_PREFIX = "avatars";

    private static final String POST_IMAGE_KEY_PREFIX = "posts";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    public S3StorageService(S3Client s3Client, S3Presigner s3Presigner, S3Properties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    /**
     * アバター画像のキーを採番する。
     *
     * <p>ファイル名にユーザーの入力を一切使わないのが要点。アップロード時のファイル名を
     * 流用すると {@code ../} を含む名前でキー空間を汚されうるし、日本語や記号の扱いも
     * ストレージごとに違う。UUID なら衝突せず、そうした問題が最初から起きない。</p>
     *
     * <p>拡張子は申告ではなく {@link ImageValidator} が検出した実フォーマットから決める。</p>
     *
     * <p>更新のたびにキーが変わるので、ブラウザや CDN に古い画像が残ったままにならない。</p>
     */
    public String newAvatarKey(Long userId, ImageValidator.ImageFormat format) {
        return "%s/%d/%s.%s".formatted(AVATAR_KEY_PREFIX, userId, UUID.randomUUID(), format.extension());
    }

    /**
     * 投稿画像のキーを採番する。
     *
     * <p>UUID を使う理由は {@link #newAvatarKey} と同じ（ユーザー入力を混ぜない・衝突しない）。
     * アバターと違い差し替えは無く、投稿削除までキーは変わらない。</p>
     */
    public String newPostImageKey(Long userId, ImageValidator.ImageFormat format) {
        return "%s/%d/%s.%s".formatted(POST_IMAGE_KEY_PREFIX, userId, UUID.randomUUID(), format.extension());
    }

    /**
     * オブジェクトを保存する。
     *
     * <p>{@link InputStream} ではなくバイト配列を受け取る。SDK は署名の計算と送信で
     * 中身を二度読むため、巻き戻せないストリーム（{@code MultipartFile} のものがそう）を
     * 渡すと「Content input stream does not support mark/reset」で失敗する。
     * 呼び出し側が上限（{@link ImageValidator#MAX_BYTES}）を検証済みなので、
     * 全体をメモリに載せても差し支えない。</p>
     */
    public void put(String key, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key)
            .contentType(contentType)
            .build();

        s3Client.putObject(request, RequestBody.fromBytes(content));
        log.debug("オブジェクトを保存しました: key={}, bytes={}", key, content.length);
    }

    /**
     * オブジェクトを削除する。失敗しても例外にせず警告だけ残す。
     *
     * <p>この削除は「古いアバターの後始末」にしか使わない。DB 側は既に新しいキーを
     * 指しているので、消し損ねても画面の見え方は変わらず、残るのは参照されない
     * オブジェクト 1 個だけ。それを理由に更新処理全体を失敗させる方が損になる。</p>
     */
    public void deleteQuietly(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build());
            log.debug("オブジェクトを削除しました: key={}", key);
        } catch (S3Exception e) {
            log.warn("オブジェクトの削除に失敗しました（参照されないまま残ります）: key={}", key, e);
        }
    }

    /**
     * 期限付きの閲覧用 URL を発行する。
     *
     * <p>バケットを公開せずに済ませるための仕組み。URL が漏れても期限が切れれば無効になる。</p>
     *
     * <p>既知のトレードオフ: 発行するたびに署名が変わるため、同じ画像でもブラウザキャッシュが
     * 効かず、タイムラインを取り直すたびに再ダウンロードになる。本番で CloudFront に載せる際は
     * 署名付き Cookie や OAC を使い、URL を安定させる方が望ましい
     * （docs/08_constraints.md に TBD として記載）。</p>
     */
    public String presignedGetUrl(String key) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(properties.avatarUrlExpirationMinutes()))
            .getObjectRequest(GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build())
            .build();

        return s3Presigner.presignGetObject(request).url().toString();
    }
}
