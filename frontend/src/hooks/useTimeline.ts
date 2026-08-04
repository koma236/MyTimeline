import { useCallback } from 'react'
import * as postsApi from '../api/posts'
import type { PostResponse, TimelineTab } from '../types/post'
import { useCursorPager } from './useCursorPager'

/**
 * タイムラインの取得とページングを引き受けるフック。
 *
 * 取得先が違うだけで仕組みは他の一覧と同じなので、ページングの実体は
 * useCursorPager に置いている。ここはタイムライン API との橋渡しと、
 * 呼び出し側にとって分かりやすい名前付け（posts / prependPost など）だけを担う。
 */
export function useTimeline(tab: TimelineTab) {
  const fetchPage = useCallback(
    async (cursor: number | null) => {
      const response = await postsApi.fetchTimeline(tab, cursor)
      return { items: response.posts, nextCursor: response.nextCursor }
    },
    [tab],
  )

  const pager = useCursorPager<PostResponse>(`timeline:${tab}`, fetchPage)

  return {
    posts: pager.items,
    loading: pager.loading,
    error: pager.error,
    hasMore: pager.hasMore,
    loadMore: pager.loadMore,
    retry: pager.retry,
    prependPost: pager.prependItem,
    replacePost: pager.replaceItem,
    patchPost: pager.patchItem,
    removePost: pager.removeItem,
  }
}
