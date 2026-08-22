import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from './client'
import {
  deleteAvatar,
  fetchProfile,
  fetchUserPosts,
  followUser,
  searchUsers,
  unfollowUser,
  updateProfile,
  uploadAvatar,
} from './users'

vi.mock('./client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  refreshSession: vi.fn(),
}))

const get = vi.mocked(apiClient.get)
const post = vi.mocked(apiClient.post)
const put = vi.mocked(apiClient.put)
const del = vi.mocked(apiClient.delete)

describe('users api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fetchProfile は username をパスに埋める', async () => {
    get.mockResolvedValue({ data: { username: 'taro' } })

    expect(await fetchProfile('taro')).toEqual({ username: 'taro' })
    expect(get).toHaveBeenCalledWith('/users/taro')
  })

  it('エラー推測: username は URL エンコードされる（パス区切りや空白を含んでも壊れない）', async () => {
    get.mockResolvedValue({ data: {} })

    await fetchProfile('a/b c')

    expect(get).toHaveBeenCalledWith('/users/a%2Fb%20c')
  })

  it.each([
    [null, undefined],
    [5, { cursor: 5 }],
  ])('同値分割: fetchUserPosts の cursor=%s → params=%o', async (cursor, params) => {
    get.mockResolvedValue({ data: { posts: [], nextCursor: null } })

    await fetchUserPosts('taro', cursor)

    expect(get).toHaveBeenCalledWith('/users/taro/posts', { params })
  })

  it.each([
    ['taro', null, { q: 'taro' }],
    ['taro', 9, { q: 'taro', cursor: 9 }],
    ['', undefined, { q: '' }],
  ])('同値分割: searchUsers(q=%s, cursor=%s) → params=%o', async (query, cursor, params) => {
    get.mockResolvedValue({ data: { users: [], nextCursor: null } })

    await searchUsers(query, cursor)

    expect(get).toHaveBeenCalledWith('/users/search', { params })
  })

  it('updateProfile は /users/me に PUT する', async () => {
    put.mockResolvedValue({ data: { displayName: '太郎' } })

    expect(await updateProfile({ displayName: '太郎', bio: '' })).toEqual({ displayName: '太郎' })
    expect(put).toHaveBeenCalledWith('/users/me', { displayName: '太郎', bio: '' })
  })

  it('uploadAvatar は file パートの multipart を PUT し、deleteAvatar は DELETE する', async () => {
    put.mockResolvedValue({ data: { avatarUrl: 'https://s3/a.png' } })
    del.mockResolvedValue({ data: { avatarUrl: null } })
    const file = new File(['x'], 'a.png', { type: 'image/png' })

    expect(await uploadAvatar(file)).toEqual({ avatarUrl: 'https://s3/a.png' })
    expect(await deleteAvatar()).toEqual({ avatarUrl: null })

    const [url, form] = put.mock.calls[0] ?? []
    expect(url).toBe('/users/me/avatar')
    expect((form as FormData).get('file')).toBe(file)
    expect(del).toHaveBeenCalledWith('/users/me/avatar')
  })

  it('followUser は POST、unfollowUser は DELETE で同じ URL を叩く', async () => {
    post.mockResolvedValue({ data: { followerCount: 1, followingByMe: true } })
    del.mockResolvedValue({ data: { followerCount: 0, followingByMe: false } })

    expect(await followUser(2)).toEqual({ followerCount: 1, followingByMe: true })
    expect(await unfollowUser(2)).toEqual({ followerCount: 0, followingByMe: false })
    expect(post).toHaveBeenCalledWith('/users/2/follow')
    expect(del).toHaveBeenCalledWith('/users/2/follow')
  })
})
