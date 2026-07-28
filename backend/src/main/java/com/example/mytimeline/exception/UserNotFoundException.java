package com.example.mytimeline.exception;

/**
 * トークンは有効だが、対象のユーザーが既に存在しない場合の例外。
 *
 * <p>{@link InvalidCredentialsException} と分けているのは文言のため。
 * あちらは「メールアドレスまたはパスワードが正しくありません」で、
 * ログイン画面でしか意味を成さない。{@code /api/auth/me} で同じ文言を返すと
 * 「入力していないのにパスワードが違うと言われる」ことになる。</p>
 *
 * <p>ステータスは 401。アカウントが消えている以上ログイン状態は続けられず、
 * クライアントにはログイン画面へ戻ってもらう必要があるため。</p>
 */
public class UserNotFoundException extends RuntimeException {

    public static final String MESSAGE = "アカウントが見つかりません。再度ログインしてください";

    public UserNotFoundException() {
        super(MESSAGE);
    }
}
