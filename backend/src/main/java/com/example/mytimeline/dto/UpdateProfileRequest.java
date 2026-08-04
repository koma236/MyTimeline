package com.example.mytimeline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * プロフィール更新リクエスト（docs/features/F07_profile.md 6. バリデーション / 制約）。
 *
 * <p>username とメールアドレスは対象外。前者は他人からの参照先（URL・メンション）になり、
 * 後者はログイン手段なので、どちらもプロフィール編集とは別の手続きにする。</p>
 *
 * <p>アバター画像は multipart の別エンドポイントで扱う。JSON と画像を 1 リクエストに
 * まとめると、片方だけ成功した状態をユーザーに説明できなくなるため。</p>
 */
public record UpdateProfileRequest(

    // 制約と文言は SignupRequest の同項目と完全に揃える。片方だけ変えると
    // 「登録できたのに編集で弾かれる」というちぐはぐな挙動になる
    @NotBlank(message = "表示名を入力してください")
    @Size(max = 100, message = "表示名は100文字以内で入力してください")
    String displayName,

    // 未設定に戻せる必要があるので @NotBlank は付けない。
    // 上限は users.bio VARCHAR(300) に合わせる
    @Size(max = 300, message = "自己紹介は300文字以内で入力してください")
    String bio
) {
}
