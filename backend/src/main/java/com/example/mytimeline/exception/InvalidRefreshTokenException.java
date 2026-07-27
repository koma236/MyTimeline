package com.example.mytimeline.exception;

/**
 * リフレッシュトークンが無効（不存在・期限切れ・失効済み）な場合に投げる。
 *
 * <p>{@link InvalidCredentialsException} と同じく、原因は呼び出し元に区別させない。
 * 「どのトークンが存在するか」を攻撃者に教えないためである。</p>
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public static final String MESSAGE = "セッションの有効期限が切れました。再度ログインしてください";

    public InvalidRefreshTokenException() {
        super(MESSAGE);
    }
}
