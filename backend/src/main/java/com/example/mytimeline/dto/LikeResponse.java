package com.example.mytimeline.dto;

/**
 * いいねの付与・取り消し後の状態（docs/features/F05_like.md）。
 *
 * <p>操作後の件数と自分のいいね状態を返すのは、クライアントがボタンの見た目を
 * 更新するのにこの 2 つだけあれば足り、投稿を取り直さずに済むため。</p>
 *
 * @param likeCount  操作後のいいね数
 * @param likedByMe  操作後に自分がいいねしている状態か
 */
public record LikeResponse(
    long likeCount,
    boolean likedByMe
) {
}
