import { useCallback } from 'react'
import * as usersApi from '../api/users'
import type { UserSummary } from '../types/user'
import { useCursorPager } from './useCursorPager'

/**
 * ユーザー検索の結果一覧（SCR-06・F06）。
 *
 * タイムラインと同じカーソルページングなので useCursorPager をそのまま使う。
 * 検索語が変わると requestKey が変わり、1 ページ目から取り直される。
 * 入力のたびに投げないための遅延（デバウンス）は画面側の責任で、このフックは
 * 「渡された検索語の結果を取る」ことだけを引き受ける。
 */
export function useUserSearch(query: string) {
  const fetchPage = useCallback(
    async (cursor: number | null) => {
      const response = await usersApi.searchUsers(query, cursor)
      return { items: response.users, nextCursor: response.nextCursor }
    },
    [query],
  )

  const pager = useCursorPager<UserSummary>(`user-search:${query}`, fetchPage)

  return {
    users: pager.items,
    loading: pager.loading,
    error: pager.error,
    hasMore: pager.hasMore,
    loadMore: pager.loadMore,
    retry: pager.retry,
    /** フォロー状態だけを差し替える。検索結果を取り直すと入力中の一覧が丸ごと入れ替わってしまう */
    patchUser: pager.patchItem,
  }
}
