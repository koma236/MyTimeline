package com.example.mytimeline.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * ログインリクエスト。
 *
 * @param identifier メールアドレスまたはユーザー名（SCR-01 の「メール または ユーザー名」）
 * @param password   パスワード
 */
public record LoginRequest(

    @NotBlank(message = "メールアドレスまたはユーザー名を入力してください")
    String identifier,

    @NotBlank(message = "パスワードを入力してください")
    String password
) {
}
