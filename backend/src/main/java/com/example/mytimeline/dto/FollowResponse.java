package com.example.mytimeline.dto;

/**
 * フォロー / フォロー解除後の状態（docs/features/F06_follow.md）。
 *
 * <p>{@link LikeResponse} と同じ考え方で、操作後の状態だけを返す。クライアントは
 * ボタンの見た目とフォロワー数をこれだけで更新でき、プロフィールを取り直さずに済む。</p>
 *
 * <p>フォロー中数（自分がフォローしている人数）は返さない。この操作で変わるのは
 * 「操作した本人のフォロー中数」と「相手のフォロワー数」だが、画面に出ているのは
 * 相手のプロフィールなので、返して意味があるのは後者だけ。</p>
 *
 * @param followerCount  操作後の、対象ユーザーのフォロワー数
 * @param followingByMe  操作後に自分が対象をフォローしている状態か
 */
public record FollowResponse(
    long followerCount,
    boolean followingByMe
) {
}
