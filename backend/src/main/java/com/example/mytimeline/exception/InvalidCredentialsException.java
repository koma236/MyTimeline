package com.example.mytimeline.exception;

/**
 * ログイン認証に失敗した場合の例外。
 *
 * <p>「ユーザーが存在しない」と「パスワードが違う」を区別せず、どちらもこの例外を投げる。
 * 区別してしまうと「そのメールアドレスは登録済み」という情報が外部に漏れるため
 * （F01 7. エラーハンドリング）。</p>
 */
public class InvalidCredentialsException extends RuntimeException {

    public static final String MESSAGE = "メールアドレスまたはパスワードが正しくありません";

    public InvalidCredentialsException() {
        super(MESSAGE);
    }
}
