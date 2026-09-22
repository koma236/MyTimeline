package com.example.mytimeline.service;

import com.example.mytimeline.dto.AuthResponse;
import com.example.mytimeline.dto.LoginRequest;
import com.example.mytimeline.dto.SignupRequest;
import com.example.mytimeline.dto.UserResponse;
import com.example.mytimeline.exception.DuplicateFieldException;
import com.example.mytimeline.exception.InvalidCredentialsException;
import com.example.mytimeline.exception.InvalidRefreshTokenException;
import com.example.mytimeline.exception.UserNotFoundException;
import com.example.mytimeline.mapper.UserMapper;
import com.example.mytimeline.model.User;
import com.example.mytimeline.security.JwtService;
import com.example.mytimeline.storage.AvatarUrlFactory;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 新規登録・ログイン・トークン更新の業務ロジック。
 *
 * <p>{@link #signup} と {@link #login} にはあえて {@code @Transactional} を付けていない。
 * BCrypt のハッシュ化・照合は約 80 ミリ秒の CPU 処理で、トランザクションの中で行うと
 * その間ずっと DB 接続を握り続ける。ログインが集中すると HikariCP の接続が占有され、
 * 無関係なリクエストまで接続待ちになる（#67）。DB を触る部分は、
 * それぞれが自分のトランザクションを持つ {@link UserRegistrar} と
 * {@link RefreshTokenService} に任せ、BCrypt の間は接続を持たないようにしている。</p>
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserRegistrar userRegistrar;
    private final AvatarUrlFactory avatarUrlFactory;

    public AuthService(
        UserMapper userMapper,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        RefreshTokenService refreshTokenService,
        UserRegistrar userRegistrar,
        AvatarUrlFactory avatarUrlFactory
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userRegistrar = userRegistrar;
        this.avatarUrlFactory = avatarUrlFactory;
    }

    /**
     * アカウントを作成し、そのままログイン状態にする（F01 2. 機能詳細）。
     *
     * <p>重複の事前チェックをハッシュ化より先に行うのは、重複で弾かれるリクエストに
     * BCrypt の CPU を使わないため。</p>
     */
    public AuthResult signup(SignupRequest request) {
        if (userMapper.findByUsername(request.username()).isPresent()) {
            throw new DuplicateFieldException("username", "このユーザー名は既に使用されています");
        }
        if (userMapper.findByEmail(request.email()).isPresent()) {
            throw new DuplicateFieldException("email", "このメールアドレスは既に登録されています");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setEmail(request.email());
        // 平文は保存しない（docs/05_nonfunctional.md セキュリティ）
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        UserRegistrar.Registration registration = userRegistrar.register(user);
        return toResult(registration.user(), registration.rawRefreshToken());
    }

    /**
     * メールアドレスまたはユーザー名とパスワードで認証し、トークンを発行する。
     */
    public AuthResult login(LoginRequest request) {
        User user = findByIdentifier(request.identifier())
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return toResult(user, refreshTokenService.issue(user.getId()));
    }

    /**
     * リフレッシュトークンを使ってアクセストークンを取り直す。
     *
     * <p>リフレッシュトークンも同時に新しい物へ差し替える（ローテーション）。
     * 使い回さないことで、漏れた古いトークンが使われた際に検知できる。</p>
     */
    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(rawRefreshToken);
        User user = userMapper.findById(rotation.userId())
            // トークンは有効だが対象ユーザーが削除済みのケース
            .orElseThrow(InvalidRefreshTokenException::new);

        return toResult(user, rotation.rawToken());
    }

    /**
     * ログアウト。提示されたリフレッシュトークンのユーザーの全セッションを失効させる。
     *
     * <p>トークンが無効・不在でも例外にしない（{@link RefreshTokenService#revoke}）。</p>
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    /**
     * ログイン中ユーザーの最新情報を取得する。
     */
    @Transactional(readOnly = true)
    public UserResponse getById(Long userId) {
        return userMapper.findById(userId)
            .map(this::toResponse)
            // トークンは有効だが対象ユーザーが削除済みのケース。
            // ログイン失敗用の文言（「メールアドレスまたはパスワードが…」）は
            // 何も入力していないこの経路では意味が通らないので使わない
            .orElseThrow(UserNotFoundException::new);
    }

    /**
     * ユーザーを DTO へ詰め替える。
     *
     * <p>アバター URL はキーから毎回組み立てる期限付きの署名なので、
     * {@code UserResponse.from} は解決済みの URL を引数で受け取る形になっている。</p>
     */
    private UserResponse toResponse(User user) {
        return UserResponse.from(user, avatarUrlFactory.urlFor(user.getAvatarKey()));
    }

    /**
     * SCR-01 の入力欄はメールアドレスとユーザー名を兼ねるため、両方で探す。
     */
    private Optional<User> findByIdentifier(String identifier) {
        return userMapper.findByEmail(identifier)
            .or(() -> userMapper.findByUsername(identifier));
    }

    private AuthResult toResult(User user, String rawRefreshToken) {
        return new AuthResult(
            new AuthResponse(jwtService.generateAccessToken(user), toResponse(user)),
            rawRefreshToken
        );
    }

    /**
     * サービス層の戻り値。
     *
     * <p>リフレッシュトークンの生値は {@link AuthResponse} に入れずここで分けて返す。
     * Cookie に載せるのは HTTP の関心事なので、組み立てはコントローラ層の責務とする。</p>
     *
     * @param response           レスポンスボディ（アクセストークン + ユーザー）
     * @param rawRefreshToken    Cookie に載せるリフレッシュトークンの生値
     */
    public record AuthResult(AuthResponse response, String rawRefreshToken) {
    }
}
