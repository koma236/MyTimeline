package com.example.mytimeline.dto;

import com.example.mytimeline.model.User;
import java.time.LocalDateTime;

/**
 * 他人にも見せるプロフィール（docs/features/F07_profile.md 4. / 画面 SCR-05）。
 *
 * <p>{@link UserResponse} を使い回さないのは、あちらがメールアドレスを含むため。
 * 誰でも開けるプロフィールで返すと個人情報が漏れる。{@link PostAuthor} と同じ判断を
 * プロフィール用に広げた形。</p>
 *
 * <p>フォロー中数・フォロワー数・フォロー済みかは follows テーブルが未作成のため含めていない。
 * F06（フォロー機能）の実装時に追加する。</p>
 *
 * @param avatarUrl アバターの閲覧用 URL。未設定なら null（クライアントは初期アバターを表示する）
 */
public record ProfileResponse(
    Long id,
    String username,
    String displayName,
    String bio,
    String avatarUrl,
    LocalDateTime createdAt
) {

    /**
     * @param avatarUrl {@code AvatarUrlFactory} が解決済みの URL。DB の値ではないため引数で受け取る
     */
    public static ProfileResponse from(User user, String avatarUrl) {
        return new ProfileResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getBio(),
            avatarUrl,
            user.getCreatedAt()
        );
    }
}
