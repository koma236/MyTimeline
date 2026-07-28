package com.example.mytimeline.exception;

/**
 * 他人のコメントを編集・削除しようとした場合の例外（docs/features/F04_comment.md 6. 認可）。
 *
 * <p>{@link PostForbiddenException} と同じく、クライアントは自分のコメントにしか操作メニューを
 * 出さないが、API を直接叩かれる可能性があるためサーバー側でも必ず所有者を確認する。</p>
 */
public class CommentForbiddenException extends RuntimeException {

    public static final String MESSAGE = "このコメントを操作する権限がありません";

    public CommentForbiddenException() {
        super(MESSAGE);
    }
}
