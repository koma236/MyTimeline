package com.example.mytimeline.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * ユーザー検索の 1 ページ分（docs/features/F06_follow.md 4. / 画面 SCR-06）。
 *
 * <p>ページングは {@link TimelineResponse} と同じカーソル方式（id の降順）。
 * 検索結果でも「読んでいる間に新しいユーザーが増えると境界がずれる」問題は同じなので、
 * 一覧を返す API はすべてこの形に揃える。</p>
 *
 * @param users      新しい順（登録が新しい順）のユーザー
 * @param nextCursor 次のページを取るためのカーソル。{@code null} なら以降は無い
 */
public record UserSearchResponse(
    List<UserSummary> users,
    @Nullable Long nextCursor
) {
}
