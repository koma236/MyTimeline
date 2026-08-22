import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from './client'
import { createComment, deleteComment, fetchComments, updateComment } from './comments'

vi.mock('./client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  refreshSession: vi.fn(),
}))

const get = vi.mocked(apiClient.get)
const post = vi.mocked(apiClient.post)
const put = vi.mocked(apiClient.put)
const del = vi.mocked(apiClient.delete)

describe('comments api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it.each([
    [undefined, undefined],
    [null, undefined],
    [3, { cursor: 3 }],
  ])('同値分割: fetchComments の cursor=%s → params=%o', async (cursor, params) => {
    get.mockResolvedValue({ data: { comments: [], nextCursor: null } })

    await fetchComments(10, cursor)

    expect(get).toHaveBeenCalledWith('/posts/10/comments', { params })
  })

  it('createComment は投稿配下に POST して data を返す', async () => {
    post.mockResolvedValue({ data: { id: 1, body: 'c' } })

    expect(await createComment(10, { body: 'c' })).toEqual({ id: 1, body: 'c' })
    expect(post).toHaveBeenCalledWith('/posts/10/comments', { body: 'c' })
  })

  it('updateComment / deleteComment はコメント id の URL を使う', async () => {
    put.mockResolvedValue({ data: { id: 1, body: 'edited' } })
    del.mockResolvedValue({ data: undefined })

    expect(await updateComment(1, { body: 'edited' })).toEqual({ id: 1, body: 'edited' })
    await deleteComment(1)

    expect(put).toHaveBeenCalledWith('/comments/1', { body: 'edited' })
    expect(del).toHaveBeenCalledWith('/comments/1')
  })
})
