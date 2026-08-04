import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthContext, type AuthContextValue, type AuthStatus } from './authContext'
import { ProtectedRoute, PublicOnlyRoute } from './RouteGuards'

function authValue(status: AuthStatus): AuthContextValue {
  return {
    user: null,
    status,
    signup: async () => {},
    login: async () => {},
    logout: async () => {},
    setCurrentUser: () => {},
  }
}

// レンダー中に組み立てると jsx-no-constructed-context-values に触れるので、
// 状態ごとの値をあらかじめ作っておく
const AUTH_VALUES: Record<AuthStatus, AuthContextValue> = {
  loading: authValue('loading'),
  authenticated: authValue('authenticated'),
  anonymous: authValue('anonymous'),
}

/** ログインが要る画面（"/"）に、指定の認証状態で入る。 */
function renderProtected(status: AuthStatus) {
  render(
    <AuthContext.Provider value={AUTH_VALUES[status]}>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<p>タイムライン</p>} />
          </Route>
          <Route path="/login" element={<p>ログイン画面</p>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

/** ログイン済みなら見せない画面（"/login"）に、指定の認証状態で入る。 */
function renderPublicOnly(status: AuthStatus) {
  render(
    <AuthContext.Provider value={AUTH_VALUES[status]}>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route element={<PublicOnlyRoute />}>
            <Route path="/login" element={<p>ログイン画面</p>} />
          </Route>
          <Route path="/" element={<p>タイムライン</p>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('ProtectedRoute', () => {
  it('セッション復元中は判定を保留する', () => {
    // ここで待たずに判定すると、リロードのたびに一瞬ログイン画面が出てしまう
    renderProtected('loading')

    expect(screen.getByText('読み込み中…')).toBeInTheDocument()
    expect(screen.queryByText('タイムライン')).not.toBeInTheDocument()
    expect(screen.queryByText('ログイン画面')).not.toBeInTheDocument()
  })

  it('ログイン済みなら中身を表示する', () => {
    renderProtected('authenticated')

    expect(screen.getByText('タイムライン')).toBeInTheDocument()
  })

  it('未ログインならログイン画面へ送る', () => {
    renderProtected('anonymous')

    expect(screen.getByText('ログイン画面')).toBeInTheDocument()
    expect(screen.queryByText('タイムライン')).not.toBeInTheDocument()
  })
})

describe('PublicOnlyRoute', () => {
  it('セッション復元中は判定を保留する', () => {
    renderPublicOnly('loading')

    expect(screen.getByText('読み込み中…')).toBeInTheDocument()
    expect(screen.queryByText('ログイン画面')).not.toBeInTheDocument()
  })

  it('未ログインならログイン画面を表示する', () => {
    renderPublicOnly('anonymous')

    expect(screen.getByText('ログイン画面')).toBeInTheDocument()
  })

  it('ログイン済みならホームへ戻す', () => {
    renderPublicOnly('authenticated')

    expect(screen.getByText('タイムライン')).toBeInTheDocument()
    expect(screen.queryByText('ログイン画面')).not.toBeInTheDocument()
  })
})
