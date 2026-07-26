package com.example.mytimeline.dto;

/**
 * 新規登録 / ログイン成功時のレスポンス。
 *
 * <p>登録直後もログイン状態にする（F01 2. 機能詳細）ため、
 * signup / login どちらもトークンを返す。</p>
 */
public record AuthResponse(String token, UserResponse user) {
}
