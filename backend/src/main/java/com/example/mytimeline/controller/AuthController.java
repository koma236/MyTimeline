package com.example.mytimeline.controller;

import com.example.mytimeline.dto.AuthResponse;
import com.example.mytimeline.dto.LoginRequest;
import com.example.mytimeline.dto.SignupRequest;
import com.example.mytimeline.dto.UserResponse;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.security.RefreshCookieFactory;
import com.example.mytimeline.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証エンドポイント（docs/features/F01_auth.md 4. API エンドポイント案）。
 *
 * <p>アクセストークンはレスポンスボディで返し、クライアントは
 * {@code Authorization: Bearer} で送る。リフレッシュトークンは httpOnly Cookie で
 * 往復させるため、クライアント側のコードが触ることはない。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;

    public AuthController(AuthService authService, RefreshCookieFactory refreshCookieFactory) {
        this.authService = authService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        AuthService.AuthResult result = authService.signup(request);
        return withRefreshCookie(ResponseEntity.status(HttpStatus.CREATED), result);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResult result = authService.login(request);
        return withRefreshCookie(ResponseEntity.ok(), result);
    }

    /**
     * アクセストークンの再発行。
     *
     * <p>アクセストークンではなく Cookie のリフレッシュトークンで認証するため、
     * このエンドポイントは permitAll になっている（{@code SecurityConfig}）。
     * アクセストークンが切れた状態で呼ばれるのが本来の用途なので、
     * 認証必須にすると成立しない。</p>
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
        @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken
    ) {
        AuthService.AuthResult result = authService.refresh(refreshToken);
        return withRefreshCookie(ResponseEntity.ok(), result);
    }

    /**
     * ログアウト。リフレッシュトークンを失効させ、Cookie を削除する。
     *
     * <p>アクセストークンは短命で失効させられないため、認可はリフレッシュトークンで行う。
     * 認証必須にしてしまうと「アクセストークンが切れているのでログアウトもできない」
     * という状態が起きるので permitAll としている。</p>
     *
     * <p>トークンが無効・不在でも 204 を返す。ログアウトの目的は
     * 「結果としてログアウト状態になること」であり、失敗を伝える意味がない。
     * また、成否を返すとトークンの存在有無を推測できてしまう。</p>
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.expired().toString())
            .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(authService.getById(currentUser.id()));
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(
        ResponseEntity.BodyBuilder builder,
        AuthService.AuthResult result
    ) {
        return builder
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(result.rawRefreshToken()).toString())
            .body(result.response());
    }
}
