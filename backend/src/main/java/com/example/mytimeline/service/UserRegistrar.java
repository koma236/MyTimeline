package com.example.mytimeline.service;

import com.example.mytimeline.mapper.UserMapper;
import com.example.mytimeline.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 新規登録のうち、DB に書き込む部分だけを 1 つのトランザクションで実行する。
 *
 * <p>BCrypt のハッシュ化は約 80 ミリ秒の CPU 処理で、トランザクションの中で行うと
 * その間ずっと DB 接続を握り続ける。登録やログインが集中すると HikariCP の接続が
 * 占有され、無関係なリクエストまで接続待ちになる（#67）。そのためハッシュ化は
 * {@link AuthService} 側でトランザクションの外で済ませ、ここには
 * ハッシュ化済みのユーザーだけを渡す。</p>
 *
 * <p>Spring の {@code @Transactional} はプロキシ経由でしか効かず、同一クラス内の
 * メソッド呼び出しでは無視される。そのため {@link AuthService} とは別のクラスに
 * 切り出している（{@link RefreshTokenRevoker} と同じ理由）。</p>
 */
@Service
public class UserRegistrar {

    private final UserMapper userMapper;
    private final RefreshTokenService refreshTokenService;

    public UserRegistrar(UserMapper userMapper, RefreshTokenService refreshTokenService) {
        this.userMapper = userMapper;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * ユーザーを保存し、リフレッシュトークンを発行する。
     *
     * <p>どちらかが失敗したら両方ともロールバックする。ユーザーだけが残ると、
     * 利用者には登録が失敗したように見えるのに、同じユーザー名では登録し直せなくなる。</p>
     */
    @Transactional
    public Registration register(User user) {
        // 事前チェックと INSERT の間に別リクエストが登録した場合は
        // UNIQUE 制約違反（DuplicateKeyException）となり GlobalExceptionHandler が 409 に変換する
        userMapper.insert(user);

        // created_at などの DB 側で採番された値を含めて返すため読み直す
        User created = userMapper.findById(user.getId()).orElseThrow();
        return new Registration(created, refreshTokenService.issue(created.getId()));
    }

    /**
     * @param user               保存後に読み直したユーザー
     * @param rawRefreshToken    Cookie に載せるリフレッシュトークンの生値
     */
    public record Registration(User user, String rawRefreshToken) {
    }
}
