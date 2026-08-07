package com.example.mytimeline.dto;

import com.example.mytimeline.model.User;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * 他人にも見せるプロフィール（docs/features/F07_profile.md 4. / 画面 SCR-05）。
 *
 * <p>{@link UserResponse} を使い回さないのは、あちらがメールアドレスを含むため。
 * 誰でも開けるプロフィールで返すと個人情報が漏れる。{@link PostAuthor} と同じ判断を
 * プロフィール用に広げた形。</p>
 *
 * @param avatarUrl      アバターの閲覧用 URL。未設定なら null（クライアントは初期アバターを表示する）
 * @param followingCount このユーザーがフォローしている人数（F06）
 * @param followerCount  このユーザーをフォローしている人数（F06）
 * @param followingByMe  ログイン中ユーザーがこのユーザーをフォロー済みか。自分自身なら常に false
 */
public record ProfileResponse(
    Long id,
    String username,
    String displayName,
    @Nullable String bio,
    @Nullable String avatarUrl,
    LocalDateTime createdAt,
    long followingCount,
    long followerCount,
    boolean followingByMe
) {

    /**
     * @param avatarUrl {@code AvatarUrlFactory} が解決済みの URL。DB の値ではないため引数で受け取る
     */
    public static ProfileResponse from(
        User user,
        String avatarUrl,
        long followingCount,
        long followerCount,
        boolean followingByMe
    ) {
        return new ProfileResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getBio(),
            avatarUrl,
            user.getCreatedAt(),
            followingCount,
            followerCount,
            followingByMe
        );
    }
}
