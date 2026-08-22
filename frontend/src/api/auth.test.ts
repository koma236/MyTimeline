import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchMe, login, logout, refresh, signup } from './auth'
import { apiClient, refreshSession } from './client'

vi.mock('./client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  refreshSession: vi.fn(),
}))

const get = vi.mocked(apiClient.get)
const post = vi.mocked(apiClient.post)

describe('auth api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('signup は /auth/signup に登録情報を POST して data を返す', async () => {
    const request = { username: 'taro', displayName: '太郎', email: 't@example.com', password: 'password123' }
    post.mockResolvedValue({ data: { accessToken: 'at', user: { id: 1 } } })

    expect(await signup(request)).toEqual({ accessToken: 'at', user: { id: 1 } })
    expect(post).toHaveBeenCalledWith('/auth/signup', request)
  })

  it('login は /auth/login に識別子とパスワードを POST する', async () => {
    post.mockResolvedValue({ data: { accessToken: 'at', user: { id: 1 } } })

    await login({ identifier: 'taro', password: 'password123' })

    expect(post).toHaveBeenCalledWith('/auth/login', { identifier: 'taro', password: 'password123' })
  })

  it('refresh は client の refreshSession そのもの（401 時の自動リフレッシュと同じ経路を共有する）', () => {
    expect(refresh).toBe(refreshSession)
  })

  it('logout は /auth/logout を POST し、戻り値は無い', async () => {
    post.mockResolvedValue({ data: undefined })

    await expect(logout()).resolves.toBeUndefined()
    expect(post).toHaveBeenCalledWith('/auth/logout')
  })

  it('fetchMe は /auth/me を GET する', async () => {
    get.mockResolvedValue({ data: { id: 1, username: 'taro' } })

    expect(await fetchMe()).toEqual({ id: 1, username: 'taro' })
    expect(get).toHaveBeenCalledWith('/auth/me')
  })
})
