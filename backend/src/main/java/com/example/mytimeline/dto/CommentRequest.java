package com.example.mytimeline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * コメントの作成・編集リクエスト（docs/features/F04_comment.md 6. バリデーション / 制約）。
 *
 * <p>{@link PostRequest} と同じく、作成と編集で受け取る内容が本文だけなので 1 つの record を
 * 共用している。上限 500 文字は comments.body の VARCHAR(500) に合わせた値。</p>
 */
public record CommentRequest(

    @NotBlank(message = "コメントを入力してください")
    @Size(max = 500, message = "コメントは500文字以内で入力してください")
    String body
) {
}
