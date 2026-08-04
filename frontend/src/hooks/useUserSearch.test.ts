import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as usersApi from '../api/users'
import type { UserSearchResponse, UserSummary } from '../types/user'
import { useUserSearch } from './useUserSearch'

vi.mock('../api/users')

const searchUsers = vi.mocked(usersApi.searchUsers)

function found(id: number): UserSummary {
  return {
    id,
    username: `user${id}`,
    displayName: `ユーザー${id}`,
    bio: null,
    avatarUrl: null,
    followingByMe: false,
  }
}

function page(ids: number[], nextCursor: number | null = null): UserSearchResponse {
  return { users: ids.map(found), nextCursor }
}

function renderSearch(query = 'taro') {
  return renderHook(({ q }: { q: string }) => useUserSearch(q), { initialProps: { q: query } })
}

const ids = (users: UserSummary[]) => users.map((user) => user.id)

describe('useUserSearch', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初回は検索語の 1 ページ目をカーソル無しで取得する', async () => {
    searchUsers.mockResolvedValue(page([3, 2], 2))

    const { result } = renderSearch('taro')

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(ids(result.current.users)).toEqual([3, 2])
    expect(result.current.hasMore).toBe(true)
    expect(searchUsers).toHaveBeenCalledWith('taro', null)
  })

  it('検索語が空でも取得する（新着ユーザーが返る）', async () => {
    searchUsers.mockResolvedValue(page([1]))

    const { result } = renderSearch('')

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(searchUsers).toHaveBeenCalledWith('', null)
  })

  it('loadMore は nextCursor を渡し、結果を末尾に足す', async () => {
    searchUsers.mockResolvedValueOnce(page([3, 2], 2)).mockResolvedValueOnce(page([1]))

    const { result } = renderSearch()
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.loadMore())

    await waitFor(() => expect(result.current.users).toHaveLength(3))
    expect(ids(result.current.users)).toEqual([3, 2, 1])
    expect(searchUsers).toHaveBeenLastCalledWith('taro', 2)
  })

  it('検索語が変わったら 1 ページ目から取り直す', async () => {
    searchUsers.mockResolvedValue(page([1]))

    const { result, rerender } = renderSearch('taro')
    await waitFor(() => expect(result.current.loading).toBe(false))

    rerender({ q: 'hanako' })

    await waitFor(() => expect(searchUsers).toHaveBeenLastCalledWith('hanako', null))
  })

  it('patchUser はフォロー状態だけを差し替える', async () => {
    // 検索結果を取り直すと、入力中の一覧が丸ごと入れ替わってしまう
    searchUsers.mockResolvedValue(page([2, 1]))

    const { result } = renderSearch()
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.patchUser(2, { followingByMe: true }))

    expect(result.current.users[0]?.followingByMe).toBe(true)
    expect(result.current.users[1]?.followingByMe).toBe(false)
  })
})
