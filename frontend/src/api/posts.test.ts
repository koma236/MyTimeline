import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from './client'
import {
  createPost,
  deletePost,
  fetchPost,
  fetchTimeline,
  likePost,
  unlikePost,
  updatePost,
} from './posts'

vi.mock('./client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  refreshSession: vi.fn(),
}))

const get = vi.mocked(apiClient.get)
const post = vi.mocked(apiClient.post)
const put = vi.mocked(apiClient.put)
const del = vi.mocked(apiClient.delete)

/**
 * 設計技法: 同値分割（cursor の有無、画像の 0 / 1 / 複数枚）。
 * API モジュールの責務は「URL・メソッド・パラメータの組み立て」と「response.data の取り出し」なので、
 * HTTP を出さずにその 2 点だけを見る。
 */
describe('posts api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('createPost', () => {
    it('ループ 0 回: 画像なしは body だけの multipart を POST する', async () => {
      post.mockResolvedValue({ data: { id: 1 } })

      const result = await createPost('こんにちは')

      expect(result).toEqual({ id: 1 })
      const [url, form] = post.mock.calls[0]
      expect(url).toBe('/posts')
      expect(form).toBeInstanceOf(FormData)
      expect((form as FormData).get('body')).toBe('こんにちは')
      expect((form as FormData).getAll('images')).toEqual([])
    })

    it('ループ 複数回: 画像は images パートに添付順で並ぶ', async () => {
      post.mockResolvedValue({ data: { id: 1 } })
      const first = new File(['a'], 'a.png', { type: 'image/png' })
      const second = new File(['b'], 'b.png', { type: 'image/png' })

      await createPost('画像付き', [first, second])

      const form = post.mock.calls[0][1] as FormData
      const images = form.getAll('images') as File[]
      expect(images.map((file) => file.name)).toEqual(['a.png', 'b.png'])
    })
  })

  it('fetchPost は /posts/{id} を GET して data を返す', async () => {
    get.mockResolvedValue({ data: { id: 5 } })

    expect(await fetchPost(5)).toEqual({ id: 5 })
    expect(get).toHaveBeenCalledWith('/posts/5')
  })

  it('updatePost は /posts/{id} に本文を PUT する', async () => {
    put.mockResolvedValue({ data: { id: 5, body: 'after' } })

    expect(await updatePost(5, { body: 'after' })).toEqual({ id: 5, body: 'after' })
    expect(put).toHaveBeenCalledWith('/posts/5', { body: 'after' })
  })

  it('deletePost は /posts/{id} を DELETE する', async () => {
    del.mockResolvedValue({ data: undefined })

    await deletePost(5)

    expect(del).toHaveBeenCalledWith('/posts/5')
  })

  describe('fetchTimeline', () => {
    it.each([
      ['all', undefined, undefined],
      ['following', null, undefined],
      ['all', 42, { cursor: 42 }],
    ] as const)('同値分割: tab=%s cursor=%s → params=%o', async (tab, cursor, params) => {
      get.mockResolvedValue({ data: { posts: [], nextCursor: null } })

      await fetchTimeline(tab, cursor)

      expect(get).toHaveBeenCalledWith(`/timeline/${tab}`, { params })
    })
  })

  it('likePost は POST、unlikePost は DELETE で同じ URL を叩く', async () => {
    post.mockResolvedValue({ data: { likeCount: 1, likedByMe: true } })
    del.mockResolvedValue({ data: { likeCount: 0, likedByMe: false } })

    expect(await likePost(7)).toEqual({ likeCount: 1, likedByMe: true })
    expect(await unlikePost(7)).toEqual({ likeCount: 0, likedByMe: false })
    expect(post).toHaveBeenCalledWith('/posts/7/like')
    expect(del).toHaveBeenCalledWith('/posts/7/like')
  })
})
