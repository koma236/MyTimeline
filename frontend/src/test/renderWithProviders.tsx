import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthContext, type AuthContextValue } from '../auth/authContext'
import type { UserResponse } from '../types/auth'
import { USER, authValue } from './fixtures'
import { LocationProbe } from './LocationProbe'

interface Options {
  /** ログイン中のユーザー。null で未ログイン */
  user?: UserResponse | null
  /** AuthContext の関数などを差し替える */
  auth?: Partial<AuthContextValue>
  /** 初期 URL */
  route?: string
  /**
   * ui を割り当てるルートのパターン（useParams を使う画面向け）。
   * 指定すると他のパスには現在地を表示するだけの要素を置くので、画面遷移を検証できる。
   */
  path?: string
}

/**
 * AuthContext と Router を備えた状態で描画する。
 * pages / Header / PostCard のように useAuth・Link・useParams を使うものはこれで描く。
 */
export function renderWithProviders(ui: ReactElement, options: Options = {}) {
  const { user = USER, auth, route = '/', path } = options
  const value = authValue({ user, status: user ? 'authenticated' : 'anonymous', ...auth })

  const result = render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={[route]}>
        {path ? (
          <Routes>
            <Route path={path} element={ui} />
            <Route path="*" element={<LocationProbe />} />
          </Routes>
        ) : (
          ui
        )}
      </MemoryRouter>
    </AuthContext.Provider>,
  )

  return { ...result, auth: value }
}
