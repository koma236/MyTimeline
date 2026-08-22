import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import * as authApi from './api/auth'
import * as postsApi from './api/posts'
import { AuthProvider } from './auth/AuthProvider'
import { USER } from './test/fixtures'
import { mockIntersectionObserver } from './test/intersectionObserver'

vi.mock('./api/auth')
vi.mock('./api/posts')
// トークン保存は差し替え、エラー整形（toApiError）は本物を使う
vi.mock('./api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./api/client')>()),
  setAccessToken: vi.fn(),
  setOnSessionExpired: vi.fn(),
}))

const refresh = vi.mocked(authApi.refresh)
const fetchTimeline = vi.mocked(postsApi.fetchTimeline)

/**
 * ルーティングと認証ガードの結線を通しで確認するスモークテスト。
 * 各画面の中身は pages のテストが見るので、ここでは「どの URL でどの画面に着くか」だけを見る。
 *
 * 設計技法: デシジョンテーブル（ログイン状態 × URL → 表示される画面）。
 */
describe('App', () => {
  function renderApp(route: string) {
    return render(
      <MemoryRouter initialEntries={[route]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    )
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockIntersectionObserver()
    fetchTimeline.mockResolvedValue({ posts: [], nextCursor: null })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('未ログインで / に来るとログイン画面へ送られ、ヘッダーは出ない', async () => {
    refresh.mockRejectedValue(new Error('401'))

    renderApp('/')

    expect(await screen.findByRole('button', { name: 'ログイン' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'ログアウト' })).not.toBeInTheDocument()
  })

  it('未ログインで未知のパスに来ても / 経由でログイン画面へ送られる', async () => {
    refresh.mockRejectedValue(new Error('401'))

    renderApp('/no/such/path')

    expect(await screen.findByRole('button', { name: 'ログイン' })).toBeInTheDocument()
  })

  it('ログイン中に /login へ来るとホームへ送られ、ヘッダーとタイムラインが出る', async () => {
    refresh.mockResolvedValue({ accessToken: 'at', user: USER })

    renderApp('/login')

    expect(await screen.findByRole('button', { name: 'ログアウト' })).toBeInTheDocument()
    expect(await screen.findByRole('tab', { name: 'すべて' })).toBeInTheDocument()
    expect(fetchTimeline).toHaveBeenCalledWith('all', null)
  })

  it('ログイン中は /search で検索画面が出る', async () => {
    refresh.mockResolvedValue({ accessToken: 'at', user: USER })

    renderApp('/search')

    expect(await screen.findByRole('searchbox')).toBeInTheDocument()
  })
})
