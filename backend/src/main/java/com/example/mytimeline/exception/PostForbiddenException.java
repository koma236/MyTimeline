package com.example.mytimeline.exception;

/**
 * 他人の投稿を編集・削除しようとした場合の例外（docs/features/F03_post.md 6. 認可）。
 *
 * <p>クライアントは自分の投稿にしか操作メニューを出さないが、API を直接叩かれる可能性が
 * あるためサーバー側でも必ず所有者を確認する。</p>
 */
public class PostForbiddenException extends RuntimeException {

    public static final String MESSAGE = "この投稿を操作する権限がありません";

    public PostForbiddenException() {
        super(MESSAGE);
    }
}
