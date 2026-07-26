package com.example.mytimeline.controller;

import com.example.mytimeline.dto.AuthResponse;
import com.example.mytimeline.dto.LoginRequest;
import com.example.mytimeline.dto.SignupRequest;
import com.example.mytimeline.dto.UserResponse;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証エンドポイント（docs/features/F01_auth.md 4. API エンドポイント案）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * ログアウト。
     *
     * <p>JWT はステートレスなため、サーバー側で発行済みトークンを失効させることはできない。
     * 実際の破棄はクライアントが保持しているトークンを削除することで行う。
     * このエンドポイントは F01 の API 定義に合わせて用意しており、
     * 将来トークン失効リストを導入する場合の差し込み口となる。</p>
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(authService.getById(currentUser.id()));
    }
}
