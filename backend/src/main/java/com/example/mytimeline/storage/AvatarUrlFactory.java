package com.example.mytimeline.storage;

import org.springframework.stereotype.Component;

/**
 * アバターのキーから、画面で使う URL を組み立てる。
 *
 * <p>「キーが未設定なら URL も無い」という判定をこの 1 箇所に閉じるための小さな部品
 * （{@code RefreshCookieFactory} と同じ位置づけ）。これがあることで、DTO を組み立てる
 * 各サービスはストレージの存在を知らずに済み、テストでもモック 1 つで差し替えられる。</p>
 */
@Component
public class AvatarUrlFactory {

    private final S3StorageService storageService;

    public AvatarUrlFactory(S3StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * @param avatarKey {@code users.avatar_key}。未設定なら null
     * @return 期限付きの閲覧 URL。キーが未設定なら null（クライアントは初期アバターを表示する）
     */
    public String urlFor(String avatarKey) {
        if (avatarKey == null) {
            return null;
        }
        return storageService.presignedGetUrl(avatarKey);
    }
}
