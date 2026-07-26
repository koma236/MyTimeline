package com.example.mytimeline.service;

import com.example.mytimeline.dto.AuthResponse;
import com.example.mytimeline.dto.LoginRequest;
import com.example.mytimeline.dto.SignupRequest;
import com.example.mytimeline.dto.UserResponse;
import com.example.mytimeline.exception.DuplicateFieldException;
import com.example.mytimeline.exception.InvalidCredentialsException;
import com.example.mytimeline.mapper.UserMapper;
import com.example.mytimeline.model.User;
import com.example.mytimeline.security.JwtService;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 新規登録とログインの業務ロジック。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * アカウントを作成し、そのままログイン状態にする（F01 2. 機能詳細）。
     */
    @Transactional
    public AuthResponse signup(SignupRequest request) {
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
        return new AuthResponse(jwtService.generateToken(created), UserResponse.from(created));
    }

    /**
     * メールアドレスまたはユーザー名とパスワードで認証し、トークンを発行する。
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = findByIdentifier(request.identifier())
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return new AuthResponse(jwtService.generateToken(user), UserResponse.from(user));
    }

    /**
     * ログイン中ユーザーの最新情報を取得する。
     */
    @Transactional(readOnly = true)
    public UserResponse getById(Long userId) {
        return userMapper.findById(userId)
            .map(UserResponse::from)
            // トークンは有効だが対象ユーザーが削除済みのケース
            .orElseThrow(InvalidCredentialsException::new);
    }

    /**
     * SCR-01 の入力欄はメールアドレスとユーザー名を兼ねるため、両方で探す。
     */
    private Optional<User> findByIdentifier(String identifier) {
        return userMapper.findByEmail(identifier)
            .or(() -> userMapper.findByUsername(identifier));
    }
}
