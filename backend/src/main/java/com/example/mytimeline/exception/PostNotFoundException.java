package com.example.mytimeline.exception;

/**
 * 指定された投稿が存在しない場合の例外（docs/features/F03_post.md 7. 異常系）。
 */
public class PostNotFoundException extends RuntimeException {

    public static final String MESSAGE = "投稿が見つかりません";

    public PostNotFoundException() {
        super(MESSAGE);
    }
}
