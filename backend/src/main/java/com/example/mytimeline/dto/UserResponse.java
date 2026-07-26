package com.example.mytimeline.dto;

import com.example.mytimeline.model.User;
import java.time.LocalDateTime;

/**
 * クライアントへ返すユーザー情報。
 *
 * <p>{@code passwordHash} は含めない。{@link User} をそのまま返さず必ずこの DTO に詰め替える。</p>
 */
public record UserResponse(
    Long id,
    String username,
    String displayName,
    String email,
    String bio,
    LocalDateTime createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getEmail(),
            user.getBio(),
            user.getCreatedAt()
        );
    }
}
