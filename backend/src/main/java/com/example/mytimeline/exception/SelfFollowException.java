package com.example.mytimeline.exception;

/**
 * 自分自身をフォローしようとした（docs/features/F06_follow.md 6.）。
 *
 * <p>UI ではそもそも自分のプロフィールにフォローボタンを出さないので、ここに来るのは
 * API を直接叩かれた場合だけ。DB のチェック制約（{@code ck_follows_not_self}）でも
 * 弾けるが、それだと 500 になってしまうため、アプリ側で先に 400 として返す。</p>
 */
public class SelfFollowException extends RuntimeException {

    public static final String MESSAGE = "自分自身をフォローすることはできません";

    public SelfFollowException() {
        super(MESSAGE);
    }
}
