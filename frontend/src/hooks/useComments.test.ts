import { act, renderHook, waitFor } from '@testing-library/react'
import { StrictMode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as commentsApi from '../api/comments'
import type { CommentListResponse, CommentResponse } from '../types/comment'
import { useComments } from './useComments'

vi.mock('../api/comments')

const fetchComments = vi.mocked(commentsApi.fetchComments)

/**
 * 設計技法: 分岐網羅（useCursorPager.test.ts と同じ分岐構造）+ エラー推測。
 * コメントはタイムラインと逆で「古い順・カーソルは id より大きい方向」なので、
 * 2 ページ目が末尾に足されること、追加したコメントが末尾に付くことを重点的に見る。
 */
function comment(id: number): CommentResponse {
  return {
    id,
    postId: 10,
    body: `コメント ${id}`,
    author: { id: 1, username: 'saki', displayName: 'さき', avatarUrl: null },
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
  }
}

function page(ids: number[], nextCursor: number | null = null): CommentListResponse {
  return { comments: ids.map(comment), nextCursor }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((settle) => {
    resolve = settle
  })
  return { promise, resolve }
}

function deferredRejectable<T>() {
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((_, fail) => {
    reject = fail
  })
  return { promise, reject }
}

const ids = (comments: CommentResponse[]) => comments.map((entry) => entry.id)

function renderComments(postId = 10, strict = false) {
  return renderHook(({ id }: { id: number }) => useComments(id), {
    initialProps: { id: postId },
    wrapper: strict ? StrictMode : undefined,
  })
}

describe('useComments', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初回はカーソル無しで 1 ページ目（古い順）を取得する', async () => {
    fetchComments.mockResolvedValue(page([1, 2], 2))

    const { result } = renderComments(10)

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(ids(result.current.comments)).toEqual([1, 2])
    expect(result.current.hasMore).toBe(true)
    expect(fetchComments).toHaveBeenCalledWith(10, null)
  })

  it('loadMore は nextCursor を渡し、新しいコメントを末尾に足す', async () => {
    fetchComments.mockResolvedValueOnce(page([1, 2], 2)).mockResolvedValueOnce(page([3]))

    const { result } = renderComments()
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.loadMore())

    await waitFor(() => expect(result.current.comments).toHaveLength(3))
    expect(ids(result.current.comments)).toEqual([1, 2, 3])
    expect(fetchComments).toHaveBeenLastCalledWith(10, 2)
    expect(result.current.hasMore).toBe(false)
  })

  it('末尾まで読んだら loadMore は何もしない', async () => {
    fetchComments.mockResolvedValue(page([1]))

    const { result } = renderComments()
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.loadMore())

    expect(fetchComments).toHaveBeenCalledTimes(1)
  })

  it('StrictMode の二重 effect でも同じ要求は 1 回しか取りに行かない', async () => {
    fetchComments.mockResolvedValue(page([1]))

    const { result } = renderComments(10, true)

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(fetchComments).toHaveBeenCalledTimes(1)
  })

  it('postId が変わったら空にして 1 ページ目から取り直す', async () => {
    fetchComments.mockResolvedValueOnce(page([1, 2], 2)).mockResolvedValueOnce(page([7]))

    const { result, rerender } = renderComments(10)
    await waitFor(() => expect(result.current.loading).toBe(false))

    rerender({ id: 11 })

    await waitFor(() => expect(ids(result.current.comments)).toEqual([7]))
    expect(fetchComments).toHaveBeenLastCalledWith(11, null)
  })

  it('切替前の投稿の応答が後から返っても反映しない（追い越しの破棄）', async () => {
    const first = deferred<CommentListResponse>()
    const second = deferred<CommentListResponse>()
    fetchComments.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

    const { result, rerender } = renderComments(10)
    rerender({ id: 11 })
    await act(async () => second.resolve(page([7])))

    await act(async () => first.resolve(page([1, 2])))

    expect(ids(result.current.comments)).toEqual([7])
    expect(result.current.loading).toBe(false)
  })

  it('取得に失敗したらエラーを保持し、retry で取り直せる', async () => {
    fetchComments.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(page([1]))

    const { result } = renderComments()
    await waitFor(() => expect(result.current.error).toBeDefined())
    expect(result.current.loading).toBe(false)

    act(() => result.current.retry())

    await waitFor(() => expect(ids(result.current.comments)).toEqual([1]))
    expect(result.current.error).toBeUndefined()
  })

  it('appendComment は末尾に足す（古い順なので新しいコメントは一番下）', async () => {
    fetchComments.mockResolvedValue(page([1, 2]))
    const { result } = renderComments()
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.appendComment(comment(3)))

    expect(ids(result.current.comments)).toEqual([1, 2, 3])
  })

  it('replaceComment は同じ id を置き換え、removeComment は取り除く。存在しない id は無視する', async () => {
    fetchComments.mockResolvedValue(page([1, 2, 3]))
    const { result } = renderComments()
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.replaceComment({ ...comment(2), body: 'edited' }))
    expect(result.current.comments[1]?.body).toBe('edited')

    act(() => result.current.removeComment(1))
    expect(ids(result.current.comments)).toEqual([2, 3])

    act(() => result.current.replaceComment(comment(99)))
    act(() => result.current.removeComment(99))
    expect(ids(result.current.comments)).toEqual([2, 3])
  })

  it('切替前の投稿の要求が後から失敗しても、エラーにしない', async () => {
    const first = deferredRejectable<CommentListResponse>()
    const second = deferred<CommentListResponse>()
    fetchComments.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

    const { result, rerender } = renderComments(10)
    rerender({ id: 11 })
    await act(async () => second.resolve(page([7])))

    await act(async () => first.reject(new Error('stale')))

    expect(result.current.error).toBeUndefined()
    expect(ids(result.current.comments)).toEqual([7])
  })
})
