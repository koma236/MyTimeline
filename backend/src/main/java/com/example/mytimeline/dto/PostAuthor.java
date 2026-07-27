package com.example.mytimeline.dto;

import com.example.mytimeline.model.User;

/**
 * 投稿レスポンスに埋め込む投稿者情報。
 *
 * <p>{@link UserResponse} と違いメールアドレスを含まない。他人の投稿にも付いて回るため、
 * タイムラインの表示に必要な項目だけに絞っている。</p>
 */
public record PostAuthor(
    Long id,
    String username,
    String displayName
) {

    public static PostAuthor from(User user) {
        return new PostAuthor(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
