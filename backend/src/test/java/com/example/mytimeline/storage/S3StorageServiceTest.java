package com.example.mytimeline.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    private static final String BUCKET = "test-bucket";

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3StorageService storageService() {
        S3Properties properties = new S3Properties(
            "http://localhost:9000", "http://localhost:9000", BUCKET, "ap-northeast-1",
            "key", "secret", true, 60
        );
        return new S3StorageService(s3Client, s3Presigner, properties);
    }

    /**
     * SDK は署名の計算と送信で中身を二度読む。巻き戻せないストリームを渡すと
     * 「Content input stream does not support mark/reset」で実行時に失敗するが、
     * これはモックでは再現しないため実機まで気付けなかった。
     *
     * <p>バイト配列から作った {@link RequestBody} は何度でも読み直せる。
     * ここではその性質そのものを検証し、同じ壊れ方に戻らないようにする。</p>
     */
    @Test
    @DisplayName("アップロードの本文は二度読んでも同じ内容を返す（署名と送信で読み直されるため）")
    void requestBodyCanBeReadMoreThanOnce() throws IOException {
        byte[] content = "画像のバイト列".getBytes(StandardCharsets.UTF_8);
        storageService().put("avatars/1/a.png", content, "image/png");

        ArgumentCaptor<RequestBody> captor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(any(PutObjectRequest.class), captor.capture());

        RequestBody body = captor.getValue();
        byte[] first = body.contentStreamProvider().newStream().readAllBytes();
        byte[] second = body.contentStreamProvider().newStream().readAllBytes();

        assertThat(first).isEqualTo(content);
        assertThat(second).isEqualTo(content);
    }

    @Test
    @DisplayName("アップロードはバケットとキー、検出した Content-Type を指定する")
    void putSpecifiesBucketKeyAndContentType() {
        storageService().put("avatars/1/a.png", new byte[] {1, 2, 3}, "image/png");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest request = captor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.key()).isEqualTo("avatars/1/a.png");
        assertThat(request.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("キーはユーザーごとに分かれ、検出した形式の拡張子が付く")
    void newAvatarKeyIsScopedByUser() {
        String key = storageService().newAvatarKey(42L, ImageValidator.ImageFormat.JPEG);

        assertThat(key).startsWith("avatars/42/").endsWith(".jpg");
    }

    @Test
    @DisplayName("同じユーザーでも呼ぶたびに違うキーになる（古い画像がキャッシュに残らない）")
    void newAvatarKeyIsUniquePerCall() {
        S3StorageService service = storageService();

        assertThat(service.newAvatarKey(1L, ImageValidator.ImageFormat.PNG))
            .isNotEqualTo(service.newAvatarKey(1L, ImageValidator.ImageFormat.PNG));
    }

    @Test
    @DisplayName("削除に失敗しても例外を投げない（孤児が残るだけで画面には影響しないため）")
    void deleteQuietlySwallowsFailure() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
            .thenThrow(S3Exception.builder().message("boom").build());

        // 例外が伝播しないことそのものが検証対象
        storageService().deleteQuietly("avatars/1/old.png");

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
}
