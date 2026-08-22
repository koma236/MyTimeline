import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as commentsApi from '../api/comments'
import * as postsApi from '../api/posts'
import { ME, apiError, comment, post } from '../test/fixtures'
import { mockIntersectionObserver } from '../test/intersectionObserver'
import { renderWithProviders } from '../test/renderWithProviders'
import { PostDetailPage } from './PostDetailPage'

vi.mock('../api/posts')
vi.mock('../api/comments')

const fetchPost = vi.mocked(postsApi.fetchPost)
const deletePost = vi.mocked(postsApi.deletePost)
const likePost = vi.mocked(postsApi.likePost)
const updatePost = vi.mocked(postsApi.updatePost)
const fetchComments = vi.mocked(commentsApi.fetchComments)
const createComment = vi.mocked(commentsApi.createComment)
const updateComment = vi.mocked(commentsApi.updateComment)
const deleteComment = vi.mocked(commentsApi.deleteComment)

/**
 * 設計技法: 同値分割（id が不正 / 投稿なし / 投稿あり）+ 状態遷移（コメント追加・削除でコメント数が増減）。
 */
describe('PostDetailPage', () => {
  function renderPage(route = '/posts/10') {
    return renderWithProviders(<PostDetailPage />, { route, path: '/posts/:id' })
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockIntersectionObserver()
    fetchComments.mockResolvedValue({ comments: [], nextCursor: null })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('投稿とコメント数見出しを出し、コメントが無ければ案内文を出す', async () => {
    fetchPost.mockResolvedValue(post({ id: 10, body: '詳細の本文', commentCount: 0 }))
    renderPage()

    expect(screen.getByText('読み込み中…')).toBeInTheDocument()
    expect(await screen.findByText('詳細の本文')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'コメント 0' })).toBeInTheDocument()
    expect(await screen.findByText(/まだコメントはありません/)).toBeInTheDocument()
    expect(fetchPost).toHaveBeenCalledWith(10)
    expect(fetchComments).toHaveBeenCalledWith(10, null)
  })

  it('同値分割: id が数値でなければ API を呼ばずに「投稿が見つかりません」を出す', async () => {
    renderPage('/posts/abc')

    expect(await screen.findByRole('alert')).toHaveTextContent('投稿が見つかりません')
    expect(fetchPost).not.toHaveBeenCalled()
    expect(screen.getByRole('link', { name: 'タイムラインへ戻る' })).toHaveAttribute('href', '/')
  })

  it('同値分割: 投稿の取得が 404 ならサーバーのメッセージを出す', async () => {
    fetchPost.mockRejectedValue(apiError(404, { message: '投稿が存在しません' }))
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('投稿が存在しません')
  })

  it('コメント一覧を古い順に出す', async () => {
    fetchPost.mockResolvedValue(post({ id: 10, commentCount: 2 }))
    fetchComments.mockResolvedValue({
      comments: [comment({ id: 1, body: '最初のコメント' }), comment({ id: 2, body: '次のコメント' })],
      nextCursor: null,
    })
    renderPage()

    await screen.findByText('最初のコメント')
    const bodies = screen.getAllByRole('article').map((article) => article.textContent ?? '')
    // 先頭は投稿本体、以降がコメント
    expect(bodies[1]).toContain('最初のコメント')
    expect(bodies[2]).toContain('次のコメント')
  })

  it('状態遷移: コメントを投稿すると末尾に足され、コメント数が 1 増える', async () => {
    fetchPost.mockResolvedValue(post({ id: 10, commentCount: 1 }))
    fetchComments.mockResolvedValue({ comments: [comment({ id: 1, body: '既存' })], nextCursor: null })
    createComment.mockResolvedValue(comment({ id: 2, body: '追加分', author: ME }))
    renderPage()
    await screen.findByText('既存')

    fireEvent.change(screen.getByLabelText('コメントの本文'), { target: { value: '追加分' } })
    await userEvent.click(screen.getByRole('button', { name: '返信' }))

    expect(await screen.findByText('追加分')).toBeInTheDocument()
    expect(createComment).toHaveBeenCalledWith(10, { body: '追加分' })
    expect(screen.getByRole('heading', { name: 'コメント 2' })).toBeInTheDocument()
    const bodies = screen.getAllByRole('article').map((article) => article.textContent ?? '')
    expect(bodies.at(-1)).toContain('追加分')
  })

  it('状態遷移: 自分のコメントを削除するとコメント数が 1 減る', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    fetchPost.mockResolvedValue(post({ id: 10, commentCount: 1 }))
    fetchComments.mockResolvedValue({ comments: [comment({ id: 1, body: '消す', author: ME })], nextCursor: null })
    deleteComment.mockResolvedValue(undefined)
    renderPage()
    await screen.findByText('消す')

    await userEvent.click(screen.getByRole('button', { name: 'コメントメニュー' }))
    await userEvent.click(screen.getByRole('button', { name: '削除' }))

    await waitFor(() => expect(screen.queryByText('消す')).not.toBeInTheDocument())
    expect(deleteComment).toHaveBeenCalledWith(1)
    expect(screen.getByRole('heading', { name: 'コメント 0' })).toBeInTheDocument()
  })

  it('いいねは投稿本体の件数と状態を差し替える', async () => {
    fetchPost.mockResolvedValue(post({ id: 10, likeCount: 0, likedByMe: false }))
    likePost.mockResolvedValue({ likeCount: 1, likedByMe: true })
    renderPage()
    const like = await screen.findByRole('button', { name: /いいね/ })

    await userEvent.click(like)

    await waitFor(() => expect(like).toHaveAttribute('aria-pressed', 'true'))
    expect(like).toHaveTextContent('1')
  })

  it('自分の投稿を削除するとタイムラインへ戻る', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    fetchPost.mockResolvedValue(post({ id: 10, author: ME }))
    deletePost.mockResolvedValue(undefined)
    renderPage()
    await screen.findByRole('button', { name: '投稿メニュー' })

    await userEvent.click(screen.getByRole('button', { name: '投稿メニュー' }))
    await userEvent.click(screen.getByRole('button', { name: '削除' }))

    expect(await screen.findByTestId('location')).toHaveTextContent('/')
    expect(deletePost).toHaveBeenCalledWith(10)
  })

  it('自分の投稿を編集すると updatePost の結果で本体を差し替える', async () => {
    fetchPost.mockResolvedValue(post({ id: 10, body: 'before', author: ME }))
    updatePost.mockResolvedValue(post({ id: 10, body: 'after', author: ME }))
    renderPage()
    await screen.findByText('before')

    await userEvent.click(screen.getByRole('button', { name: '投稿メニュー' }))
    await userEvent.click(screen.getByRole('button', { name: '編集' }))
    fireEvent.change(screen.getByLabelText('投稿の本文を編集'), { target: { value: 'after' } })
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('after')).toBeInTheDocument()
    expect(updatePost).toHaveBeenCalledWith(10, { body: 'after' })
  })

  it('自分のコメントを編集すると updateComment の結果で一覧を差し替える', async () => {
    fetchPost.mockResolvedValue(post({ id: 10, commentCount: 1 }))
    fetchComments.mockResolvedValue({ comments: [comment({ id: 1, body: 'before', author: ME })], nextCursor: null })
    updateComment.mockResolvedValue(comment({ id: 1, body: 'after', author: ME }))
    renderPage()
    await screen.findByText('before')

    await userEvent.click(screen.getByRole('button', { name: 'コメントメニュー' }))
    await userEvent.click(screen.getByRole('button', { name: '編集' }))
    fireEvent.change(screen.getByLabelText('コメントの本文を編集'), { target: { value: 'after' } })
    await userEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('after')).toBeInTheDocument()
    expect(updateComment).toHaveBeenCalledWith(1, { body: 'after' })
  })
})
