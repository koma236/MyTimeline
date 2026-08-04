import { useCallback } from 'react'
import * as usersApi from '../api/users'
import type { PostResponse } from '../types/post'
import { useCursorPager } from './useCursorPager'

/**
 * 特定ユーザーの投稿一覧（SCR-05 プロフィール画面）。
 *
 * タイムラインと同じ形で返るので useTimeline と同じ仕組みをそのまま使う。
 * patchPost / replacePost / removePost も付いてくるため、プロフィール上でも
 * いいね・編集・削除がタイムラインと同じコードで動く。
 */
export function useUserPosts(username: string) {
  const fetchPage = useCallback(
    async (cursor: number | null) => {
      const response = await usersApi.fetchUserPosts(username, cursor)
      return { items: response.posts, nextCursor: response.nextCursor }
    },
    [username],
  )

  const pager = useCursorPager<PostResponse>(`user-posts:${username}`, fetchPage)

  return {
    posts: pager.items,
    loading: pager.loading,
    error: pager.error,
    hasMore: pager.hasMore,
    loadMore: pager.loadMore,
    retry: pager.retry,
    replacePost: pager.replaceItem,
    patchPost: pager.patchItem,
    removePost: pager.removeItem,
  }
}
