import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import * as authApi from '../api/auth'
import { setAccessToken, setOnSessionExpired } from '../api/client'
import type { LoginRequest, SignupRequest, UserResponse } from '../types/auth'
import { AuthContext, type AuthContextValue, type AuthStatus } from './authContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null)
  const [status, setStatus] = useState<AuthStatus>('loading')

  const clearSession = useCallback(() => {
    setAccessToken(null)
    setUser(null)
    setStatus('anonymous')
  }, [])

  // 自動リフレッシュにも失敗したら、この Provider の状態も未ログインへ落とす
  useEffect(() => {
    setOnSessionExpired(clearSession)
    return () => setOnSessionExpired(null)
  }, [clearSession])

  /**
   * 起動時のセッション復元。
   *
   * アクセストークンはメモリにしか持たずリロードで消えるため、Cookie の
   * リフレッシュトークンで取り直すこの経路だけがログイン状態の復元手段になる。
   * refresh はユーザー情報も返すので /me を追加で呼ぶ必要はない。
   */
  useEffect(() => {
    let cancelled = false

    authApi
      .refresh()
      .then((response) => {
        if (cancelled) return
        setUser(response.user)
        setStatus('authenticated')
      })
      .catch(() => {
        if (cancelled) return
        // Cookie が無い / 失効している。未ログインとして扱う
        clearSession()
      })

    return () => {
      cancelled = true
    }
  }, [clearSession])

  const signup = useCallback(async (request: SignupRequest) => {
    const response = await authApi.signup(request)
    setAccessToken(response.accessToken)
    setUser(response.user)
    setStatus('authenticated')
  }, [])

  const login = useCallback(async (request: LoginRequest) => {
    const response = await authApi.login(request)
    setAccessToken(response.accessToken)
    setUser(response.user)
    setStatus('authenticated')
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      // サーバー側の失効に失敗しても、手元のトークンは必ず捨てる
    } finally {
      clearSession()
    }
  }, [clearSession])

  /**
   * プロフィール編集の結果を反映する。
   *
   * 未ログインの状態で呼ばれても状態を作り直さないよう、既にユーザーが
   * いるときだけ差し替える（ログインの経路は signup / login が担う）。
   */
  const setCurrentUser = useCallback((updated: UserResponse) => {
    setUser((current) => (current == null ? current : updated))
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ user, status, signup, login, logout, setCurrentUser }),
    [user, status, signup, login, logout, setCurrentUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
