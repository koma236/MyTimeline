import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as postsApi from '../api/posts'
import * as usersApi from '../api/users'
import { ME, USER, apiError, post, profile } from '../test/fixtures'
import { mockIntersectionObserver } from '../test/intersectionObserver'
import { renderWithProviders } from '../test/renderWithProviders'
import { ProfilePage } from './ProfilePage'

vi.mock('../api/posts')
vi.mock('../api/users')

const fetchProfile = vi.mocked(usersApi.fetchProfile)
const fetchUserPosts = vi.mocked(usersApi.fetchUserPosts)
const followUser = vi.mocked(usersApi.followUser)
const likePost = vi.mocked(postsApi.likePost)

/**
 * 設計技法: 同値分割（自分 / 他人、投稿 0 件 / あり、見つからない）+ 状態遷移（フォローでフォロワー数が増える）。
 */
describe('ProfilePage', () => {
  function renderPage(username = 'taro') {
    return renderWithProviders(<ProfilePage />, { route: `/users/${username}`, path: '/users/:username' })
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockIntersectionObserver()
    fetchUserPosts.mockResolvedValue({ posts: [], nextCursor: null })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('他人のプロフィール: 表示名・@username・利用開始・フォロー数とフォローボタンを出す', async () => {
    fetchProfile.mockResolvedValue(profile({ bio: 'こんにちは', followingCount: 3, followerCount: 5 }))
    renderPage('taro')

    expect(await screen.findByRole('heading', { name: '山田太郎' })).toBeInTheDocument()
    expect(screen.getByText('@taro')).toBeInTheDocument()
    expect(screen.getByText('2025年3月から利用')).toBeInTheDocument()
    expect(screen.getByText('こんにちは')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'フォロー' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'プロフィールを編集' })).not.toBeInTheDocument()
    expect(fetchProfile).toHaveBeenCalledWith('taro')
    expect(fetchUserPosts).toHaveBeenCalledWith('taro', null)
  })

  it('自分のプロフィール: フォローボタンの代わりに編集リンクを出す', async () => {
    fetchProfile.mockResolvedValue(profile({ id: USER.id, username: USER.username, displayName: USER.displayName }))
    renderPage('alice')

    expect(await screen.findByRole('link', { name: 'プロフィールを編集' })).toHaveAttribute('href', '/settings/profile')
    expect(screen.queryByRole('button', { name: /フォロー/ })).not.toBeInTheDocument()
    expect(await screen.findByText('まだ投稿がありません。最初の投稿をしてみましょう。')).toBeInTheDocument()
  })

  it('他人の投稿 0 件は短い案内文', async () => {
    fetchProfile.mockResolvedValue(profile())
    renderPage('taro')

    expect(await screen.findByText('まだ投稿がありません。')).toBeInTheDocument()
  })

  it('状態遷移: フォローするとフォロワー数とボタンの状態が更新される', async () => {
    fetchProfile.mockResolvedValue(profile({ followerCount: 5, followingByMe: false }))
    followUser.mockResolvedValue({ followerCount: 6, followingByMe: true })
    renderPage('taro')

    await userEvent.click(await screen.findByRole('button', { name: 'フォロー' }))

    expect(await screen.findByRole('button', { name: 'フォロー中' })).toBeInTheDocument()
    expect(screen.getByText('6')).toBeInTheDocument()
    expect(followUser).toHaveBeenCalledWith(2)
  })

  it('同値分割: 存在しないユーザーはサーバーのメッセージと戻る導線を出す', async () => {
    fetchProfile.mockRejectedValue(apiError(404, { message: 'ユーザーが存在しません' }))
    renderPage('nobody')

    expect(await screen.findByRole('alert')).toHaveTextContent('ユーザーが存在しません')
    expect(screen.getByRole('link', { name: 'タイムラインへ戻る' })).toHaveAttribute('href', '/')
  })

  it('投稿一覧を出し、いいねで件数を差し替える', async () => {
    fetchProfile.mockResolvedValue(profile())
    fetchUserPosts.mockResolvedValue({ posts: [post({ id: 1, body: 'taro の投稿', likeCount: 0 })], nextCursor: null })
    likePost.mockResolvedValue({ likeCount: 1, likedByMe: true })
    renderPage('taro')

    expect(await screen.findByText('taro の投稿')).toBeInTheDocument()
    const like = screen.getByRole('button', { name: /いいね/ })
    await userEvent.click(like)

    await waitFor(() => expect(like).toHaveAttribute('aria-pressed', 'true'))
    expect(likePost).toHaveBeenCalledWith(1)
  })

  it('投稿一覧の取得エラーはプロフィールを残したまま alert と再読み込みを出す', async () => {
    fetchProfile.mockResolvedValue(profile())
    fetchUserPosts.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce({
      posts: [post({ id: 1, body: '復帰', author: ME })],
      nextCursor: null,
    })
    renderPage('taro')

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '山田太郎' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '再読み込み' }))

    expect(await screen.findByText('復帰')).toBeInTheDocument()
  })
})
