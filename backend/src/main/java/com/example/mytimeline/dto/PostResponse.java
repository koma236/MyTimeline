package com.example.mytimeline.dto;

import com.example.mytimeline.model.Post;
import java.time.LocalDateTime;

/**
 * クライアントへ返す投稿（docs/features/F02_timeline.md 4. レスポンス（投稿要素）例）。
 *
 * <p>「自分の投稿か」を表すフラグは持たない。クライアントは
 * {@code author.id} とログインユーザーの id を比べれば判定できるため。</p>
 *
 * <p>画像（images）といいね数・コメント数は、対応するテーブルが未作成のため含めていない。
 * 画像は F03 の画像対応時、like_count / comment_count / liked_by_me は F04・F05 で追加する。</p>
 */
public record PostResponse(
    Long id,
    String body,
    PostAuthor author,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static PostResponse from(Post post) {
        return new PostResponse(
            post.getId(),
            post.getBody(),
            PostAuthor.from(post.getAuthor()),
            post.getCreatedAt(),
            post.getUpdatedAt()
        );
    }
}
