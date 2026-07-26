package com.example.mytimeline.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新規登録リクエスト（docs/features/F01_auth.md 6. バリデーション / 制約）。
 */
public record SignupRequest(

    @NotBlank(message = "ユーザー名を入力してください")
    @Pattern(
        regexp = "^[A-Za-z0-9_]{3,50}$",
        message = "ユーザー名は半角英数字とアンダースコアで3〜50文字にしてください"
    )
    String username,

    @NotBlank(message = "表示名を入力してください")
    @Size(max = 100, message = "表示名は100文字以内で入力してください")
    String displayName,

    @NotBlank(message = "メールアドレスを入力してください")
    @Email(message = "メールアドレスの形式が正しくありません")
    @Size(max = 255, message = "メールアドレスは255文字以内で入力してください")
    String email,

    @NotBlank(message = "パスワードを入力してください")
    @Size(min = 8, max = 100, message = "パスワードは8文字以上100文字以内で入力してください")
    String password
) {
}
