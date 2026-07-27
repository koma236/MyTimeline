package com.example.mytimeline.dto;

/**
 * 新規登録 / ログイン / トークン更新の成功時レスポンス。
 *
 * <p>登録直後もログイン状態にする（F01 2. 機能詳細）ため、
 * signup / login どちらもアクセストークンを返す。</p>
 *
 * <p>リフレッシュトークンはこのボディには含めない。httpOnly Cookie で返すため、
 * JSON に載せると JavaScript から読めてしまい httpOnly にした意味が無くなる。</p>
 */
public record AuthResponse(String accessToken, UserResponse user) {
}
