import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as usersApi from '../api/users'
import type { PostResponse, TimelineResponse } from '../types/post'
import { useUserPosts } from './useUserPosts'

vi.mock('../api/users')

const fetchUserPosts = vi.mocked(usersApi.fetchUserPosts)

function post(id: number): PostResponse {
  return {
    id,
    body: `投稿 ${id}`,
    author: { id: 1, username: 'saki', displayName: 'さき', avatarUrl: null },
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

function renderUserPosts(username = 'saki') {
  return renderHook(({ name }: { name: string }) => useUserPosts(name), {
    initialProps: { name: username },
  })
}

const ids = (posts: PostResponse[]) => posts.map((item) => item.id)

describe('useUserPosts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初回は対象ユーザーの 1 ページ目をカーソル無しで取得する', async () => {
    fetchUserPosts.mockResolvedValue(page([3, 2], 2))

    const { result } = renderUserPosts('saki')

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(ids(result.current.posts)).toEqual([3, 2])
    expect(result.current.hasMore).toBe(true)
    expect(fetchUserPosts).toHaveBeenCalledWith('saki', null)
  })

  it('loadMore は nextCursor を渡し、結果を末尾に足す', async () => {
    fetchUserPosts.mockResolvedValueOnce(page([3, 2], 2)).mockResolvedValueOnce(page([1]))

    const { result } = renderUserPosts()
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.loadMore())

    await waitFor(() => expect(result.current.posts).toHaveLength(3))
    expect(ids(result.current.posts)).toEqual([3, 2, 1])
    expect(fetchUserPosts).toHaveBeenLastCalledWith('saki', 2)
    expect(result.current.hasMore).toBe(false)
  })

  it('ユーザーが変わったら 1 ページ目から取り直す', async () => {
    fetchUserPosts.mockResolvedValue(page([1]))

    const { result, rerender } = renderUserPosts('saki')
    await waitFor(() => expect(result.current.loading).toBe(false))

    rerender({ name: 'taro' })

    await waitFor(() => expect(fetchUserPosts).toHaveBeenLastCalledWith('taro', null))
  })

  it('patchPost は該当の投稿だけを部分更新する', async () => {
    // いいねの API は投稿全体ではなく { likeCount, likedByMe } しか返さない
    fetchUserPosts.mockResolvedValue(page([2, 1]))

    const { result } = renderUserPosts()
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.patchPost(2, { likeCount: 5, likedByMe: true }))

    expect(result.current.posts[0]?.likeCount).toBe(5)
    expect(result.current.posts[0]?.likedByMe).toBe(true)
    // もう一方は元のまま
    expect(result.current.posts[1]?.likeCount).toBe(0)
  })

  it('removePost は削除した投稿を取り除く', async () => {
    fetchUserPosts.mockResolvedValue(page([2, 1]))

    const { result } = renderUserPosts()
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.removePost(2))

    expect(ids(result.current.posts)).toEqual([1])
  })

  it('取得に失敗するとエラーを持ち、retry で取り直せる', async () => {
    fetchUserPosts.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(page([1]))

    const { result } = renderUserPosts()
    await waitFor(() => expect(result.current.error).toBeDefined())

    act(() => result.current.retry())

    // error は取得の開始時点で消えるので、それを待つと取得完了前に通ってしまう。
    // 実際に届いたかどうかは posts で確かめる
    await waitFor(() => expect(ids(result.current.posts)).toEqual([1]))
    expect(result.current.error).toBeUndefined()
  })
})
