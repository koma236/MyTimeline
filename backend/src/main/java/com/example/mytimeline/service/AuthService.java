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
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 新規登録・ログイン・トークン更新の業務ロジック。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
        UserMapper userMapper,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        RefreshTokenService refreshTokenService
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * アカウントを作成し、そのままログイン状態にする（F01 2. 機能詳細）。
     */
    @Transactional
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

        // 事前チェックと INSERT の間に別リクエストが登録した場合は
        // UNIQUE 制約違反（DuplicateKeyException）となり GlobalExceptionHandler が 409 に変換する
        userMapper.insert(user);

        // created_at などの DB 側で採番された値を含めて返すため読み直す
        User created = userMapper.findById(user.getId()).orElseThrow();
        return issueFor(created);
    }

    /**
     * メールアドレスまたはユーザー名とパスワードで認証し、トークンを発行する。
     */
    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = findByIdentifier(request.identifier())
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return issueFor(user);
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

        return new AuthResult(
            new AuthResponse(jwtService.generateAccessToken(user), UserResponse.from(user)),
            rotation.rawToken()
        );
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
            .map(UserResponse::from)
            // トークンは有効だが対象ユーザーが削除済みのケース。
            // ログイン失敗用の文言（「メールアドレスまたはパスワードが…」）は
            // 何も入力していないこの経路では意味が通らないので使わない
            .orElseThrow(UserNotFoundException::new);
    }

    /**
     * SCR-01 の入力欄はメールアドレスとユーザー名を兼ねるため、両方で探す。
     */
    private Optional<User> findByIdentifier(String identifier) {
        return userMapper.findByEmail(identifier)
            .or(() -> userMapper.findByUsername(identifier));
    }

    private AuthResult issueFor(User user) {
        return new AuthResult(
            new AuthResponse(jwtService.generateAccessToken(user), UserResponse.from(user)),
            refreshTokenService.issue(user.getId())
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
