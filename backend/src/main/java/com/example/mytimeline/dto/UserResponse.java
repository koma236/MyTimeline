package com.example.mytimeline.dto;

import com.example.mytimeline.model.User;
import java.time.LocalDateTime;

/**
 * クライアントへ返すユーザー情報。
 *
 * <p>{@code passwordHash} は含めない。{@link User} をそのまま返さず必ずこの DTO に詰め替える。</p>
 *
 * <p>メールアドレスを含むため、返してよいのは<b>本人に対してだけ</b>。他人のプロフィールには
 * {@link ProfileResponse} を使うこと。</p>
 *
 * @param avatarUrl アバターの閲覧用 URL。未設定なら null
 */
public record UserResponse(
    Long id,
    String username,
    String displayName,
    String email,
    String bio,
    String avatarUrl,
    LocalDateTime createdAt
) {

    /**
     * @param avatarUrl {@code AvatarUrlFactory} が解決済みの URL。DB の値ではないため引数で受け取る
     */
    public static UserResponse from(User user, String avatarUrl) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getEmail(),
            user.getBio(),
            avatarUrl,
            user.getCreatedAt()
        );
    }
}
