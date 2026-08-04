package com.example.mytimeline.exception;

/**
 * 指定された username のユーザーが存在しない（docs/features/F07_profile.md 7.）。
 *
 * <p>{@link UserNotFoundException} とは別物なので流用しないこと。あちらは
 * 「トークンは有効なのに本人のレコードが無い」という認証側の異常で 401 に変換される。
 * こちらは他人のプロフィールを開こうとして見つからなかった場合の 404。</p>
 */
public class ProfileNotFoundException extends RuntimeException {

    public static final String MESSAGE = "ユーザーが見つかりません";

    public ProfileNotFoundException() {
        super(MESSAGE);
    }
}
