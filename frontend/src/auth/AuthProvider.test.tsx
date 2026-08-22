import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useContext, useState } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../api/auth'
import { setAccessToken, setOnSessionExpired } from '../api/client'
import type { UserResponse } from '../types/auth'
import { AuthProvider } from './AuthProvider'
import { AuthContext } from './authContext'

vi.mock('../api/auth')
vi.mock('../api/client')

const refresh = vi.mocked(authApi.refresh)
const login = vi.mocked(authApi.login)
const signup = vi.mocked(authApi.signup)
const logout = vi.mocked(authApi.logout)
const mockedSetAccessToken = vi.mocked(setAccessToken)
const mockedSetOnSessionExpired = vi.mocked(setOnSessionExpired)

const USER: UserResponse = {
  id: 1,
  username: 'alice',
  displayName: 'アリス',
  email: 'alice@example.com',
  bio: null,
  avatarUrl: null,
  createdAt: '2026-01-01T00:00:00',
}

/**
 * 設計技法: 状態遷移。
 *
 *   loading ──refresh 成功──▶ authenticated ──logout / セッション失効──▶ anonymous
 *      └────refresh 失敗──▶ anonymous ──login / signup──▶ authenticated
 *
 * 各遷移で「状態」「user」「アクセストークンの保存 / 破棄」の 3 つが揃って変わることを見る。
 */
function Probe() {
  const auth = useContext(AuthContext)
  const [failure, setFailure] = useState('')
  if (auth == null) throw new Error('AuthProvider の外で使われた')
  return (
    <div>
      <output data-testid="status">{auth.status}</output>
      <output data-testid="user">{auth.user?.displayName ?? '-'}</output>
      <output data-testid="failure">{failure}</output>
      <button
        onClick={() =>
          auth.login({ identifier: 'alice', password: 'pw' }).catch(() => setFailure('login failed'))
        }
      >
        login
      </button>
      <button
        onClick={() =>
          auth
            .signup({ username: 'alice', displayName: 'アリス', email: 'a@example.com', password: 'pw' })
            .catch(() => setFailure('signup failed'))
        }
      >
        signup
      </button>
      <button onClick={() => void auth.logout()}>logout</button>
      <button onClick={() => auth.setCurrentUser({ ...USER, displayName: '更新後' })}>update</button>
    </div>
  )
}

function renderProvider() {
  return render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )
}

const status = () => screen.getByTestId('status').textContent
const userName = () => screen.getByTestId('user').textContent

describe('AuthProvider', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('起動時のセッション復元', () => {
    it('状態遷移: loading → authenticated（Cookie のリフレッシュトークンで復元できた）', async () => {
      refresh.mockResolvedValue({ accessToken: 'at', user: USER })

      renderProvider()

      expect(status()).toBe('loading')
      await waitFor(() => expect(status()).toBe('authenticated'))
      expect(userName()).toBe('アリス')
      expect(refresh).toHaveBeenCalledTimes(1)
    })

    it('状態遷移: loading → anonymous（Cookie が無い / 失効）。手元のトークンも捨てる', async () => {
      refresh.mockRejectedValue(new Error('401'))

      renderProvider()

      await waitFor(() => expect(status()).toBe('anonymous'))
      expect(userName()).toBe('-')
      expect(mockedSetAccessToken).toHaveBeenCalledWith(null)
    })

    it('エラー推測: 復元の応答より先にアンマウントされたら状態を更新しない', async () => {
      let settle!: (value: { accessToken: string; user: UserResponse }) => void
      refresh.mockReturnValue(
        new Promise((resolve) => {
          settle = resolve
        }),
      )
      const { unmount } = renderProvider()
      const warn = vi.spyOn(console, 'error').mockImplementation(() => {})

      unmount()
      await act(async () => settle({ accessToken: 'at', user: USER }))

      // アンマウント後の setState があれば React が console.error を出す
      expect(warn).not.toHaveBeenCalled()
      warn.mockRestore()
    })
  })

  describe('ログイン / 新規登録', () => {
    beforeEach(() => {
      refresh.mockRejectedValue(new Error('401'))
    })

    it('状態遷移: anonymous → authenticated（login）。アクセストークンを保存し user を持つ', async () => {
      login.mockResolvedValue({ accessToken: 'new-token', user: USER })
      renderProvider()
      await waitFor(() => expect(status()).toBe('anonymous'))

      await userEvent.click(screen.getByRole('button', { name: 'login' }))

      await waitFor(() => expect(status()).toBe('authenticated'))
      expect(userName()).toBe('アリス')
      expect(login).toHaveBeenCalledWith({ identifier: 'alice', password: 'pw' })
      expect(mockedSetAccessToken).toHaveBeenLastCalledWith('new-token')
    })

    it('状態遷移: anonymous → authenticated（signup）', async () => {
      signup.mockResolvedValue({ accessToken: 'new-token', user: USER })
      renderProvider()
      await waitFor(() => expect(status()).toBe('anonymous'))

      await userEvent.click(screen.getByRole('button', { name: 'signup' }))

      await waitFor(() => expect(status()).toBe('authenticated'))
      expect(mockedSetAccessToken).toHaveBeenLastCalledWith('new-token')
    })

    it('login が失敗したら anonymous のまま。例外は呼び出し側へ伝わる（画面がエラーを出せるように）', async () => {
      login.mockRejectedValue(new Error('401'))
      renderProvider()
      await waitFor(() => expect(status()).toBe('anonymous'))
      mockedSetAccessToken.mockClear()

      await userEvent.click(screen.getByRole('button', { name: 'login' }))

      await waitFor(() => expect(screen.getByTestId('failure').textContent).toBe('login failed'))
      expect(status()).toBe('anonymous')
      expect(mockedSetAccessToken).not.toHaveBeenCalled()
    })
  })

  describe('ログアウトとセッション失効', () => {
    beforeEach(() => {
      refresh.mockResolvedValue({ accessToken: 'at', user: USER })
    })

    it('状態遷移: authenticated → anonymous（logout）。サーバーの失効を呼び、トークンを捨てる', async () => {
      logout.mockResolvedValue()
      renderProvider()
      await waitFor(() => expect(status()).toBe('authenticated'))

      await userEvent.click(screen.getByRole('button', { name: 'logout' }))

      await waitFor(() => expect(status()).toBe('anonymous'))
      expect(userName()).toBe('-')
      expect(logout).toHaveBeenCalledTimes(1)
      expect(mockedSetAccessToken).toHaveBeenLastCalledWith(null)
    })

    it('エラー推測: サーバー側の logout が失敗しても、手元は必ず anonymous にする', async () => {
      logout.mockRejectedValue(new Error('network'))
      renderProvider()
      await waitFor(() => expect(status()).toBe('authenticated'))

      await userEvent.click(screen.getByRole('button', { name: 'logout' }))

      await waitFor(() => expect(status()).toBe('anonymous'))
      expect(mockedSetAccessToken).toHaveBeenLastCalledWith(null)
    })

    it('状態遷移: 自動リフレッシュの失敗（setOnSessionExpired 経由）でも anonymous に落ちる', async () => {
      renderProvider()
      await waitFor(() => expect(status()).toBe('authenticated'))
      const handler = mockedSetOnSessionExpired.mock.calls.at(-1)?.[0]
      expect(handler).toBeTypeOf('function')

      act(() => handler?.())

      expect(status()).toBe('anonymous')
      expect(userName()).toBe('-')
    })

    it('アンマウント時に失効ハンドラの登録を解除する', async () => {
      const { unmount } = renderProvider()
      await waitFor(() => expect(status()).toBe('authenticated'))

      unmount()

      expect(mockedSetOnSessionExpired).toHaveBeenLastCalledWith(null)
    })
  })

  describe('setCurrentUser', () => {
    it('同値分割（authenticated）: ログイン中ならユーザー情報を差し替える', async () => {
      refresh.mockResolvedValue({ accessToken: 'at', user: USER })
      renderProvider()
      await waitFor(() => expect(status()).toBe('authenticated'))

      await userEvent.click(screen.getByRole('button', { name: 'update' }))

      expect(userName()).toBe('更新後')
      expect(status()).toBe('authenticated')
    })

    it('同値分割（anonymous）: 未ログインなら無視し、ログイン状態を作り出さない', async () => {
      refresh.mockRejectedValue(new Error('401'))
      renderProvider()
      await waitFor(() => expect(status()).toBe('anonymous'))

      await userEvent.click(screen.getByRole('button', { name: 'update' }))

      expect(userName()).toBe('-')
      expect(status()).toBe('anonymous')
    })
  })

  it('エラー推測: 復元の失敗がアンマウント後に返っても状態を更新しない', async () => {
    let fail!: (reason: unknown) => void
    refresh.mockReturnValue(
      new Promise((_, reject) => {
        fail = reject
      }),
    )
    const { unmount } = renderProvider()
    const warn = vi.spyOn(console, 'error').mockImplementation(() => {})

    unmount()
    await act(async () => fail(new Error('401')))

    expect(warn).not.toHaveBeenCalled()
    expect(mockedSetAccessToken).not.toHaveBeenCalled()
    warn.mockRestore()
  })
})
