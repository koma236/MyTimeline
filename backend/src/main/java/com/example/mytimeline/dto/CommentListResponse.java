package com.example.mytimeline.dto;

import java.util.List;

/**
 * 投稿 1 件分のコメントの 1 ページ（docs/features/F04_comment.md 4. API エンドポイント案）。
 *
 * <p>{@link TimelineResponse} と同じカーソル方式。ただし並びは古い順なので、カーソルは
 * 「最後に読んだコメントの id」で、次のページはそれ<em>より新しい</em>コメントになる。</p>
 *
 * @param comments   古い順のコメント
 * @param nextCursor 次のページを取るためのカーソル。{@code null} なら以降は無い
 */
public record CommentListResponse(
    List<CommentResponse> comments,
    Long nextCursor
) {
}
