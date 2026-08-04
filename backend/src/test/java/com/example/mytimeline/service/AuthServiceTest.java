package com.example.mytimeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mytimeline.dto.LoginRequest;
import com.example.mytimeline.dto.SignupRequest;
import com.example.mytimeline.exception.DuplicateFieldException;
import com.example.mytimeline.exception.InvalidCredentialsException;
import com.example.mytimeline.exception.InvalidRefreshTokenException;
import com.example.mytimeline.exception.UserNotFoundException;
import com.example.mytimeline.mapper.UserMapper;
import com.example.mytimeline.model.User;
import com.example.mytimeline.security.JwtProperties;
import com.example.mytimeline.security.JwtService;
import com.example.mytimeline.storage.AvatarUrlFactory;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String RAW_PASSWORD = "password123";
    private static final String ISSUED_REFRESH_TOKEN = "issued-refresh-token";

    @Mock
    private UserMapper userMapper;

    @Mock
    private RefreshTokenService refreshTokenService;

    /**
     * アバター URL の組み立ては署名付き URL の発行を伴うためモックにする。
     * ここでは何も stub しないので、常に null（＝アバター未設定）として振る舞う。
     */
    @Mock
    private AvatarUrlFactory avatarUrlFactory;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService(
            new JwtProperties("test-secret-key-for-unit-test-at-least-32-bytes", 60, 14, false)
        );
        authService = new AuthService(
            userMapper, passwordEncoder, jwtService, refreshTokenService, avatarUrlFactory
        );
    }

    private SignupRequest signupRequest() {
        return new SignupRequest("taro", "山田太郎", "taro@example.com", RAW_PASSWORD);
    }

    private User existingUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("taro");
        user.setDisplayName("山田太郎");
        user.setEmail("taro@example.com");
        user.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        return user;
    }

    @Test
    @DisplayName("登録時はパスワードをハッシュ化して保存し、平文は保存しない")
    void signupStoresHashedPassword() {
        when(userMapper.findByUsername("taro")).thenReturn(Optional.empty());
        when(userMapper.findByEmail("taro@example.com")).thenReturn(Optional.empty());
        when(userMapper.findById(any())).thenReturn(Optional.of(existingUser()));
        when(refreshTokenService.issue(1L)).thenReturn(ISSUED_REFRESH_TOKEN);

        AuthService.AuthResult result = authService.signup(signupRequest());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        String storedHash = captor.getValue().getPasswordHash();

        assertThat(storedHash).isNotEqualTo(RAW_PASSWORD);
        assertThat(passwordEncoder.matches(RAW_PASSWORD, storedHash)).isTrue();
        assertThat(result.response().accessToken()).isNotBlank();
        assertThat(result.response().user().username()).isEqualTo("taro");
    }

    @Test
    @DisplayName("登録時はリフレッシュトークンも発行し、レスポンスボディには含めない")
    void signupIssuesRefreshTokenOutsideBody() {
        when(userMapper.findByUsername("taro")).thenReturn(Optional.empty());
        when(userMapper.findByEmail("taro@example.com")).thenReturn(Optional.empty());
        when(userMapper.findById(any())).thenReturn(Optional.of(existingUser()));
        when(refreshTokenService.issue(1L)).thenReturn(ISSUED_REFRESH_TOKEN);

        AuthService.AuthResult result = authService.signup(signupRequest());

        assertThat(result.rawRefreshToken()).isEqualTo(ISSUED_REFRESH_TOKEN);
        // リフレッシュトークンは Cookie で返すため、ボディのアクセストークンとは別物であること
        assertThat(result.response().accessToken()).isNotEqualTo(ISSUED_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("ユーザー名が重複していれば username を指す例外を投げ、INSERT しない")
    void signupRejectsDuplicateUsername() {
        when(userMapper.findByUsername("taro")).thenReturn(Optional.of(existingUser()));

        assertThatThrownBy(() -> authService.signup(signupRequest()))
            .isInstanceOf(DuplicateFieldException.class)
            .satisfies(e -> assertThat(((DuplicateFieldException) e).getField()).isEqualTo("username"));

        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("メールアドレスが重複していれば email を指す例外を投げ、INSERT しない")
    void signupRejectsDuplicateEmail() {
        when(userMapper.findByUsername("taro")).thenReturn(Optional.empty());
        when(userMapper.findByEmail("taro@example.com")).thenReturn(Optional.of(existingUser()));

        assertThatThrownBy(() -> authService.signup(signupRequest()))
            .isInstanceOf(DuplicateFieldException.class)
            .satisfies(e -> assertThat(((DuplicateFieldException) e).getField()).isEqualTo("email"));

        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("メールアドレスでログインできる")
    void loginWithEmail() {
        when(userMapper.findByEmail("taro@example.com")).thenReturn(Optional.of(existingUser()));
        when(refreshTokenService.issue(1L)).thenReturn(ISSUED_REFRESH_TOKEN);

        AuthService.AuthResult result = authService.login(new LoginRequest("taro@example.com", RAW_PASSWORD));

        assertThat(result.response().accessToken()).isNotBlank();
        assertThat(result.response().user().email()).isEqualTo("taro@example.com");
        assertThat(result.rawRefreshToken()).isEqualTo(ISSUED_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("ユーザー名でもログインできる")
    void loginWithUsername() {
        when(userMapper.findByEmail("taro")).thenReturn(Optional.empty());
        when(userMapper.findByUsername("taro")).thenReturn(Optional.of(existingUser()));
        when(refreshTokenService.issue(1L)).thenReturn(ISSUED_REFRESH_TOKEN);

        AuthService.AuthResult result = authService.login(new LoginRequest("taro", RAW_PASSWORD));

        assertThat(result.response().user().username()).isEqualTo("taro");
    }

    @Test
    @DisplayName("パスワードが違えば認証失敗し、リフレッシュトークンも発行しない")
    void loginRejectsWrongPassword() {
        when(userMapper.findByEmail("taro@example.com")).thenReturn(Optional.of(existingUser()));

        assertThatThrownBy(() -> authService.login(new LoginRequest("taro@example.com", "wrongpassword")))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage(InvalidCredentialsException.MESSAGE);

        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    @DisplayName("ユーザーが存在しない場合もパスワード誤りと同じ例外・同じメッセージになる")
    void loginDoesNotRevealWhetherUserExists() {
        when(userMapper.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(userMapper.findByUsername("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", RAW_PASSWORD)))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage(InvalidCredentialsException.MESSAGE);
    }

    @Test
    @DisplayName("リフレッシュすると新しいアクセストークンと新しいリフレッシュトークンを返す")
    void refreshRotatesBothTokens() {
        when(refreshTokenService.rotate("old-token"))
            .thenReturn(new RefreshTokenService.RotationResult(1L, "new-token"));
        when(userMapper.findById(1L)).thenReturn(Optional.of(existingUser()));

        AuthService.AuthResult result = authService.refresh("old-token");

        assertThat(result.response().accessToken()).isNotBlank();
        assertThat(result.response().user().username()).isEqualTo("taro");
        assertThat(result.rawRefreshToken()).isEqualTo("new-token");
    }

    @Test
    @DisplayName("Cookie が無い状態のリフレッシュは 401 相当の例外になる")
    void refreshWithoutCookieIsRejected() {
        assertThatThrownBy(() -> authService.refresh(null))
            .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> authService.refresh("  "))
            .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenService, never()).rotate(any());
    }

    @Test
    @DisplayName("トークンは有効でもユーザーが削除済みならリフレッシュは失敗する")
    void refreshFailsWhenUserIsGone() {
        when(refreshTokenService.rotate("old-token"))
            .thenReturn(new RefreshTokenService.RotationResult(1L, "new-token"));
        when(userMapper.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("old-token"))
            .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("ログアウトはリフレッシュトークンの失効に委譲する")
    void logoutRevokesRefreshToken() {
        authService.logout("some-token");

        verify(refreshTokenService).revoke("some-token");
    }

    @Test
    @DisplayName("削除済みユーザーの取得は、ログイン失敗とは別の文言の例外になる")
    void getByIdFailsWithUserNotFoundWhenUserIsGone() {
        when(userMapper.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getById(1L))
            .isInstanceOf(UserNotFoundException.class)
            // 何も入力していない /me で「メールアドレスまたはパスワードが…」とは言わない
            .hasMessage(UserNotFoundException.MESSAGE);
    }
}
