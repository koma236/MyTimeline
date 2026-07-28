package com.example.mytimeline.dto;

import com.example.mytimeline.model.Comment;
import java.time.LocalDateTime;

/**
 * クライアントへ返すコメント（docs/features/F04_comment.md）。
 *
 * <p>{@link PostResponse} と同じく「自分のコメントか」を表すフラグは持たない。
 * クライアントは {@code author.id} とログインユーザーの id を比べれば判定できるため。</p>
 *
 * <p>投稿者情報は投稿と同じ {@link PostAuthor} を使い回す。コメントの表示に必要な項目
 * （id・username・表示名）が投稿と同じで、別の型を作る理由がないため。</p>
 */
public record CommentResponse(
    Long id,
    Long postId,
    String body,
    PostAuthor author,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
            comment.getId(),
            comment.getPostId(),
            comment.getBody(),
            PostAuthor.from(comment.getAuthor()),
            comment.getCreatedAt(),
            comment.getUpdatedAt()
        );
    }
}
