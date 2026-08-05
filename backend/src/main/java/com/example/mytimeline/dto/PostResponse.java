package com.example.mytimeline.dto;

import com.example.mytimeline.model.Post;
import java.time.LocalDateTime;
import java.util.List;

/**
 * クライアントへ返す投稿（docs/features/F02_timeline.md 4. レスポンス（投稿要素）例）。
 *
 * <p>「自分の投稿か」を表すフラグは持たない。クライアントは
 * {@code author.id} とログインユーザーの id を比べれば判定できるため。
 * 一方 {@code likedByMe} は id の比較では導けないのでサーバーが返す。</p>
 *
 * @param imageUrls    添付画像の閲覧用 URL（表示順）。無ければ空リスト（F03）
 * @param likeCount    いいね数（F05）
 * @param commentCount コメント数（F04）
 * @param likedByMe    リクエストしたユーザーがこの投稿にいいね済みか
 */
public record PostResponse(
    Long id,
    String body,
    PostAuthor author,
    List<String> imageUrls,
    long likeCount,
    long commentCount,
    boolean likedByMe,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    /**
     * @param authorAvatarUrl 投稿者のアバター URL（{@code AvatarUrlFactory} が解決済みのもの）
     * @param imageUrls       添付画像の閲覧用 URL（キーからの解決は呼び出し側の責務）
     */
    public static PostResponse from(Post post, String authorAvatarUrl, List<String> imageUrls) {
        return new PostResponse(
            post.getId(),
            post.getBody(),
            PostAuthor.from(post.getAuthor(), authorAvatarUrl),
            imageUrls,
            post.getLikeCount(),
            post.getCommentCount(),
            post.isLikedByMe(),
            post.getCreatedAt(),
            post.getUpdatedAt()
        );
    }
}
