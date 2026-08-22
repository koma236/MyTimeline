import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as usersApi from '../api/users'
import { USER, userSummary } from '../test/fixtures'
import { mockIntersectionObserver } from '../test/intersectionObserver'
import { renderWithProviders } from '../test/renderWithProviders'
import { SearchPage } from './SearchPage'

vi.mock('../api/users')

const searchUsers = vi.mocked(usersApi.searchUsers)
const followUser = vi.mocked(usersApi.followUser)

/**
 * 設計技法: 同値分割（検索語 空 / あり × 結果 0 / あり、自分 / 他人）+ エラー推測（デバウンス、前後の空白）。
 * デバウンス（300ms）は実時間で待つ。fake timers にすると user-event の入力と噛み合わなくなるため。
 */
describe('SearchPage', () => {
  const input = () => screen.getByRole('searchbox')
  let io: ReturnType<typeof mockIntersectionObserver>

  beforeEach(() => {
    vi.clearAllMocks()
    io = mockIntersectionObserver()
    searchUsers.mockResolvedValue({ users: [], nextCursor: null })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('初期表示は空の検索語で新着ユーザーを取りに行き、0 件なら案内文を出す', async () => {
    renderWithProviders(<SearchPage />)

    await waitFor(() => expect(searchUsers).toHaveBeenCalledWith('', null))
    expect(await screen.findByText('まだ他のユーザーがいません。')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '← タイムライン' })).toHaveAttribute('href', '/')
  })

  it('入力はデバウンスされ、前後の空白を除いた検索語で 1 回だけ問い合わせる', async () => {
    renderWithProviders(<SearchPage />)
    await waitFor(() => expect(searchUsers).toHaveBeenCalledTimes(1))

    await userEvent.type(input(), '  taro ')

    await waitFor(() => expect(searchUsers).toHaveBeenLastCalledWith('taro', null), { timeout: 1500 })
    // 1 文字ごとには問い合わせない（初回 + 確定後の 1 回）
    expect(searchUsers).toHaveBeenCalledTimes(2)
  })

  it('結果には表示名・@username・プロフィールへのリンクを出し、自分にはフォローボタンを出さない', async () => {
    searchUsers.mockResolvedValue({
      users: [
        userSummary({ id: USER.id, username: USER.username, displayName: USER.displayName }),
        userSummary({ id: 2, username: 'taro', displayName: '山田太郎', bio: '自己紹介' }),
      ],
      nextCursor: null,
    })
    renderWithProviders(<SearchPage />)

    expect(await screen.findByText('@taro')).toBeInTheDocument()
    expect(screen.getByText('自己紹介')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /山田太郎/ })[0]).toHaveAttribute('href', '/users/taro')
    // フォローボタンは他人の分だけ
    expect(screen.getAllByRole('button', { name: /フォロー/ })).toHaveLength(1)
  })

  it('状態遷移: フォローすると結果のボタンが「フォロー中」になる', async () => {
    searchUsers.mockResolvedValue({ users: [userSummary({ id: 2, followingByMe: false })], nextCursor: null })
    followUser.mockResolvedValue({ followerCount: 1, followingByMe: true })
    renderWithProviders(<SearchPage />)

    await userEvent.click(await screen.findByRole('button', { name: 'フォロー' }))

    expect(await screen.findByRole('button', { name: 'フォロー中' })).toBeInTheDocument()
    expect(followUser).toHaveBeenCalledWith(2)
  })

  it('検索語があって 0 件なら「該当するユーザーがいません」', async () => {
    renderWithProviders(<SearchPage />)

    await userEvent.type(input(), 'zzz')

    expect(await screen.findByText('該当するユーザーがいません。', {}, { timeout: 1500 })).toBeInTheDocument()
  })

  it('取得エラーは alert と再読み込みを出し、押すと取り直す', async () => {
    searchUsers.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce({
      users: [userSummary({ displayName: '復帰したユーザー' })],
      nextCursor: null,
    })
    renderWithProviders(<SearchPage />)

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '再読み込み' }))

    expect(await screen.findByText('復帰したユーザー')).toBeInTheDocument()
  })

  it('続きがあれば番兵を置き、交差したらカーソル付きで次のページを取る', async () => {
    searchUsers
      .mockResolvedValueOnce({ users: [userSummary({ id: 2, displayName: '1 ページ目' })], nextCursor: 2 })
      .mockResolvedValueOnce({ users: [userSummary({ id: 3, displayName: '2 ページ目' })], nextCursor: null })
    renderWithProviders(<SearchPage />)
    await screen.findByText('1 ページ目')

    act(() => io.intersect(true))

    expect(await screen.findByText('2 ページ目')).toBeInTheDocument()
    expect(searchUsers).toHaveBeenLastCalledWith('', 2)
  })
})
