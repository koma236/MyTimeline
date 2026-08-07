package com.example.mytimeline.dto;

import com.example.mytimeline.model.User;
import org.jspecify.annotations.Nullable;

/**
 * 投稿レスポンスに埋め込む投稿者情報。
 *
 * <p>{@link UserResponse} と違いメールアドレスを含まない。他人の投稿にも付いて回るため、
 * タイムラインの表示に必要な項目だけに絞っている。</p>
 *
 * @param avatarUrl アバターの閲覧用 URL。未設定なら null（クライアントは初期アバターを表示する）
 */
public record PostAuthor(
    Long id,
    String username,
    String displayName,
    @Nullable String avatarUrl
) {

    /**
     * {@code avatarUrl} は DB の値そのものではなく期限付きの署名から作るため、
     * 解決済みの URL を引数で受け取る（組み立ては {@code AvatarUrlFactory} の責務）。
     */
    public static PostAuthor from(User user, String avatarUrl) {
        return new PostAuthor(user.getId(), user.getUsername(), user.getDisplayName(), avatarUrl);
    }
}
