package com.example.mytimeline.exception;

/**
 * 指定されたコメントが存在しない場合の例外（docs/features/F04_comment.md 7. 異常系）。
 */
public class CommentNotFoundException extends RuntimeException {

    public static final String MESSAGE = "コメントが見つかりません";

    public CommentNotFoundException() {
        super(MESSAGE);
    }
}
