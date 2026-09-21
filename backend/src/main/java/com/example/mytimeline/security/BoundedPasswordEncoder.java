package com.example.mytimeline.security;

import java.util.concurrent.Semaphore;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * パスワードのハッシュ化・照合の同時実行数を絞る {@link PasswordEncoder}。
 *
 * <p>BCrypt は 1 回あたり約 80 ミリ秒 CPU を使い切る処理で、同時に走らせても
 * CPU コア数より速くはならない。絞らないと、ログインが集中したときに
 * リクエストスレッド（Tomcat の既定で最大 200）がすべて BCrypt で CPU を奪い合い、
 * 数ミリ秒で終わるはずの無関係なリクエストまで CPU の順番待ちで遅くなる（#67）。
 * 同時実行数を絞っても、ログインの処理能力（CPU で決まる）は変わらない。</p>
 *
 * <p>順番待ちは先着順（fair）にしている。そうしないと、後から来たリクエストに
 * 追い越され続けて、いつまでも終わらないログインが出うる。</p>
 */
// final にしているのは SpotBugs（CT_CONSTRUCTOR_THROW）対応。コンストラクタで引数検証の例外を投げるクラスは、
// サブクラス化できると finalizer 経由で未初期化のインスタンスを掴まれうるため
public final class BoundedPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;
    private final Semaphore permits;

    /**
     * @param delegate         実際にハッシュ化・照合を行うエンコーダ
     * @param maxConcurrency   同時に実行してよい数（1 以上）
     */
    public BoundedPasswordEncoder(PasswordEncoder delegate, int maxConcurrency) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency は 1 以上にしてください: " + maxConcurrency);
        }
        this.delegate = delegate;
        this.permits = new Semaphore(maxConcurrency, true);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        acquire();
        try {
            return delegate.encode(rawPassword);
        } finally {
            permits.release();
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        acquire();
        try {
            return delegate.matches(rawPassword, encodedPassword);
        } finally {
            permits.release();
        }
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        // ハッシュの書式を見るだけで CPU を使わないため、絞らない
        return delegate.upgradeEncoding(encodedPassword);
    }

    private void acquire() {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            // シャットダウンなどで割り込まれた場合。割り込み状態を戻し、認証は失敗として扱わせる
            Thread.currentThread().interrupt();
            throw new IllegalStateException("パスワード処理の順番待ち中に割り込まれました", e);
        }
    }
}
