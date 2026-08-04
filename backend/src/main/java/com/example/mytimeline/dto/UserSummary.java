package com.example.mytimeline.dto;

import com.example.mytimeline.model.User;

/**
 * ユーザー検索結果の 1 件（docs/features/F06_follow.md 4. / 画面 SCR-06）。
 *
 * <p>検索結果カードに出す項目だけを持つ。{@link ProfileResponse} を使い回さないのは、
 * あちらがフォロー中数・フォロワー数を含むため。一覧の件数分だけ COUNT を走らせることになり、
 * カードには出さない値のために N+1 を作ることになる。</p>
 *
 * @param avatarUrl     アバターの閲覧用 URL。未設定なら null（クライアントは初期アバターを表示する）
 * @param followingByMe ログイン中ユーザーがこのユーザーをフォロー済みか
 */
public record UserSummary(
    Long id,
    String username,
    String displayName,
    String bio,
    String avatarUrl,
    boolean followingByMe
) {

    /**
     * @param avatarUrl {@code AvatarUrlFactory} が解決済みの URL。DB の値ではないため引数で受け取る
     */
    public static UserSummary from(User user, String avatarUrl, boolean followingByMe) {
        return new UserSummary(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getBio(),
            avatarUrl,
            followingByMe
        );
    }
}
