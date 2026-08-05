import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as postsApi from '../api/posts'
import type { PostResponse, TimelineResponse, TimelineTab } from '../types/post'
import { useTimeline } from './useTimeline'

vi.mock('../api/posts')

const fetchTimeline = vi.mocked(postsApi.fetchTimeline)

function post(id: number): PostResponse {
  return {
    id,
    body: `投稿 ${id}`,
    author: { id: 1, username: 'saki', displayName: 'さき', avatarUrl: null },
    imageUrls: [],
    likeCount: 0,
    commentCount: 0,
    likedByMe: false,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
  }
}

function page(ids: number[], nextCursor: number | null = null): TimelineResponse {
  return { posts: ids.map(post), nextCursor }
}

/** 解決のタイミングをテスト側で握るための待ち合わせ。 */
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((settle) => {
    resolve = settle
  })
  return { promise, resolve }
}

function renderTimeline(tab: TimelineTab = 'all') {
  return renderHook(({ tab: current }: { tab: TimelineTab }) => useTimeline(current), {
    initialProps: { tab },
  })
}

const ids = (posts: PostResponse[]) => posts.map((item) => item.id)

describe('useTimeline', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初回はカーソル無しで 1 ページ目を取得する', async () => {
    fetchTimeline.mockResolvedValue(page([3, 2], 2))

    const { result } = renderTimeline('all')

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(ids(result.current.posts)).toEqual([3, 2])
    expect(result.current.hasMore).toBe(true)
    expect(fetchTimeline).toHaveBeenCalledWith('all', null)
  })

  it('loadMore は nextCursor を渡し、結果を末尾に足す', async () => {
    fetchTimeline.mockResolvedValueOnce(page([3, 2], 2)).mockResolvedValueOnce(page([1]))

    const { result } = renderTimeline('all')
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.loadMore())

    await waitFor(() => expect(result.current.posts).toHaveLength(3))
    // 置き換えではなく追記（カーソルページングなので前ページは残る）
    expect(ids(result.current.posts)).toEqual([3, 2, 1])
    expect(fetchTimeline).toHaveBeenLastCalledWith('all', 2)
    expect(result.current.hasMore).toBe(false)
  })

  it('末尾まで読んだら loadMore は何もしない', async () => {
    fetchTimeline.mockResolvedValue(page([3, 2]))

    const { result } = renderTimeline('all')
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.loadMore())

    expect(fetchTimeline).toHaveBeenCalledTimes(1)
  })

  it('タブを切り替えたら 1 ページ目から取り直す', async () => {
    fetchTimeline.mockResolvedValueOnce(page([3, 2], 2)).mockResolvedValueOnce(page([9]))

    const { result, rerender } = renderTimeline('all')
    await waitFor(() => expect(ids(result.current.posts)).toEqual([3, 2]))

    rerender({ tab: 'following' })

    await waitFor(() => expect(ids(result.current.posts)).toEqual([9]))
    // 前のタブの投稿が残っていないこと。カーソルも先頭に戻ること
    expect(fetchTimeline).toHaveBeenLastCalledWith('following', null)
  })

  it('追い越されたレスポンスは破棄する', async () => {
    // タブを素早く切り替えると切替前のリクエストが後から返る。
    // そのまま反映すると別タブの投稿が一覧に紛れ込む（#17 で修正した箇所）
    const following = deferred<TimelineResponse>()
    const all = deferred<TimelineResponse>()
    fetchTimeline.mockImplementation((tab) =>
      tab === 'following' ? following.promise : all.promise,
    )

    const { result, rerender } = renderTimeline('following')
    rerender({ tab: 'all' })

    // 後から要求した all タブが先に返る
    await act(async () => {
      all.resolve(page([9]))
      await all.promise
    })
    expect(ids(result.current.posts)).toEqual([9])

    // 追い越された following の応答が遅れて届いても混ざらない
    await act(async () => {
      following.resolve(page([1, 2]))
      await following.promise
    })
    expect(ids(result.current.posts)).toEqual([9])
    expect(result.current.loading).toBe(false)
  })

  it('取得に失敗したらエラーを出し、retry で取り直せる', async () => {
    fetchTimeline.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(page([3]))

    const { result } = renderTimeline('all')
    await waitFor(() => expect(result.current.error).toBeTruthy())
    expect(result.current.loading).toBe(false)

    act(() => result.current.retry())

    await waitFor(() => expect(ids(result.current.posts)).toEqual([3]))
    expect(result.current.error).toBeUndefined()
  })

  it('投稿の追加・部分更新・削除を一覧へ反映する', async () => {
    fetchTimeline.mockResolvedValue(page([3, 2]))

    const { result } = renderTimeline('all')
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.prependPost(post(4)))
    expect(ids(result.current.posts)).toEqual([4, 3, 2])

    // いいねの API は投稿全体を返さないので部分更新が要る
    act(() => result.current.patchPost(3, { likeCount: 1, likedByMe: true }))
    expect(result.current.posts.find((item) => item.id === 3)).toMatchObject({
      likeCount: 1,
      likedByMe: true,
    })

    act(() => result.current.replacePost({ ...post(2), body: '編集後' }))
    expect(result.current.posts.find((item) => item.id === 2)?.body).toBe('編集後')

    act(() => result.current.removePost(4))
    expect(ids(result.current.posts)).toEqual([3, 2])
  })
})
