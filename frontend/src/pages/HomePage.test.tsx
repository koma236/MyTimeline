import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as postsApi from '../api/posts'
import { ME, post } from '../test/fixtures'
import { mockIntersectionObserver } from '../test/intersectionObserver'
import { renderWithProviders } from '../test/renderWithProviders'
import type { TimelineResponse } from '../types/post'
import { HomePage } from './HomePage'

vi.mock('../api/posts')

const fetchTimeline = vi.mocked(postsApi.fetchTimeline)
const createPost = vi.mocked(postsApi.createPost)
const likePost = vi.mocked(postsApi.likePost)
const unlikePost = vi.mocked(postsApi.unlikePost)
const updatePost = vi.mocked(postsApi.updatePost)
const deletePost = vi.mocked(postsApi.deletePost)

function page(bodies: string[], nextCursor: number | null = null): TimelineResponse {
  return { posts: bodies.map((body, index) => post({ id: 100 - index, body })), nextCursor }
}

/**
 * 設計技法: 同値分割（タブ × 件数 0 / あり × エラー）+ 状態遷移（投稿作成 → 先頭に追加、いいね ⇄ 取り消し）。
 * 子コンポーネントは本物を使い、API だけをモックして画面全体の振る舞いを見る。
 */
describe('HomePage', () => {
  let io: ReturnType<typeof mockIntersectionObserver>

  beforeEach(() => {
    vi.clearAllMocks()
    io = mockIntersectionObserver()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('初期表示は「すべて」タブで 1 ページ目を取り、投稿を新しい順に出す', async () => {
    fetchTimeline.mockResolvedValue(page(['新しい投稿', '古い投稿']))

    renderWithProviders(<HomePage />)

    expect(screen.getByText('読み込み中…')).toBeInTheDocument()
    expect(await screen.findByText('新しい投稿')).toBeInTheDocument()
    expect(screen.getByText('古い投稿')).toBeInTheDocument()
    expect(fetchTimeline).toHaveBeenCalledWith('all', null)
    expect(screen.getByRole('tab', { name: 'すべて' })).toHaveAttribute('aria-selected', 'true')
  })

  it('タブを切り替えると following で取り直す', async () => {
    fetchTimeline.mockResolvedValue(page([]))
    renderWithProviders(<HomePage />)
    await waitFor(() => expect(fetchTimeline).toHaveBeenCalledTimes(1))

    await userEvent.click(screen.getByRole('tab', { name: 'フォロー中' }))

    await waitFor(() => expect(fetchTimeline).toHaveBeenLastCalledWith('following', null))
  })

  it('同値分割（0 件）: すべて では案内文、フォロー中 では検索への導線を出す', async () => {
    fetchTimeline.mockResolvedValue(page([]))
    renderWithProviders(<HomePage />)

    expect(await screen.findByText('まだ誰も投稿していません。')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: 'フォロー中' }))

    expect(await screen.findByRole('link', { name: 'ユーザーを検索してフォローする' })).toHaveAttribute(
      'href',
      '/search',
    )
  })

  it('同値分割（エラー）: alert と再読み込みボタンを出し、押すと取り直す', async () => {
    fetchTimeline.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(page(['復帰']))
    renderWithProviders(<HomePage />)

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '再読み込み' }))

    expect(await screen.findByText('復帰')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('状態遷移: 投稿を作成すると createPost を呼び、結果を一覧の先頭に足す', async () => {
    fetchTimeline.mockResolvedValue(page(['既存の投稿']))
    createPost.mockResolvedValue(post({ id: 999, body: '新規投稿', author: ME }))
    renderWithProviders(<HomePage />)
    await screen.findByText('既存の投稿')

    fireEvent.change(screen.getByLabelText('投稿の本文'), { target: { value: '新規投稿' } })
    await userEvent.click(screen.getByRole('button', { name: '投稿' }))

    expect(await screen.findByText('新規投稿')).toBeInTheDocument()
    expect(createPost).toHaveBeenCalledWith('新規投稿', [])
    const bodies = screen.getAllByRole('article').map((article) => article.textContent)
    expect(bodies[0]).toContain('新規投稿')
    expect(bodies[1]).toContain('既存の投稿')
  })

  it('状態遷移: いいねは likePost、いいね済みなら unlikePost を呼び、件数と状態を差し替える', async () => {
    fetchTimeline.mockResolvedValue({ posts: [post({ id: 1, likeCount: 0, likedByMe: false })], nextCursor: null })
    likePost.mockResolvedValue({ likeCount: 1, likedByMe: true })
    unlikePost.mockResolvedValue({ likeCount: 0, likedByMe: false })
    renderWithProviders(<HomePage />)
    const like = await screen.findByRole('button', { name: /いいね/ })

    await userEvent.click(like)
    await waitFor(() => expect(like).toHaveAttribute('aria-pressed', 'true'))
    expect(like).toHaveTextContent('1')

    await userEvent.click(like)
    await waitFor(() => expect(like).toHaveAttribute('aria-pressed', 'false'))
    expect(likePost).toHaveBeenCalledWith(1)
    expect(unlikePost).toHaveBeenCalledWith(1)
  })

  it('自分の投稿は編集・削除でき、一覧に反映される', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    fetchTimeline.mockResolvedValue({ posts: [post({ id: 1, body: 'before', author: ME })], nextCursor: null })
    updatePost.mockResolvedValue(post({ id: 1, body: 'after', author: ME }))
    deletePost.mockResolvedValue(undefined)
    renderWithProviders(<HomePage />)
    await screen.findByText('before')

    await userEvent.click(screen.getByRole('button', { name: '投稿メニュー' }))
    await userEvent.click(screen.getByRole('button', { name: '編集' }))
    fireEvent.change(screen.getByLabelText('投稿の本文を編集'), { target: { value: 'after' } })
    await userEvent.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByText('after')).toBeInTheDocument()
    expect(updatePost).toHaveBeenCalledWith(1, { body: 'after' })

    await userEvent.click(screen.getByRole('button', { name: '投稿メニュー' }))
    await userEvent.click(screen.getByRole('button', { name: '削除' }))
    await waitFor(() => expect(screen.queryByText('after')).not.toBeInTheDocument())
    expect(deletePost).toHaveBeenCalledWith(1)
  })

  it('続きがあるときだけ番兵を置き、交差したら次のページを取る', async () => {
    fetchTimeline.mockResolvedValueOnce(page(['1 ページ目'], 50)).mockResolvedValueOnce(page(['2 ページ目']))
    renderWithProviders(<HomePage />)
    await screen.findByText('1 ページ目')
    expect(io.instances).toHaveLength(1)

    act(() => io.intersect(true))

    expect(await screen.findByText('2 ページ目')).toBeInTheDocument()
    expect(fetchTimeline).toHaveBeenLastCalledWith('all', 50)
    // 末尾に達したら番兵は消える（新しい observer は作られない）
    expect(io.instances).toHaveLength(1)
  })
})
