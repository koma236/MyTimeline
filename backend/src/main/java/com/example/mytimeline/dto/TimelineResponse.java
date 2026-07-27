package com.example.mytimeline.dto;

import java.util.List;

/**
 * タイムラインの 1 ページ分（docs/features/F02_timeline.md 4. API エンドポイント案）。
 *
 * <p>オフセットではなくカーソル方式にしている。オフセットだと読んでいる間に新しい投稿が
 * 増えたときに境界がずれ、同じ投稿が二度出たり飛ばされたりするため。</p>
 *
 * @param posts      新しい順の投稿
 * @param nextCursor 次のページを取るためのカーソル。{@code null} なら以降は無い
 */
public record TimelineResponse(
    List<PostResponse> posts,
    Long nextCursor
) {
}
