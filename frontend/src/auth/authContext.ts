import { createContext } from 'react'
import type { LoginRequest, SignupRequest, UserResponse } from '../types/auth'

/**
 * - loading: 起動時のセッション復元中。この間は画面の出し分けを判断してはいけない
 * - authenticated / anonymous: 確定した状態
 */
export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export interface AuthContextValue {
  user: UserResponse | null
  status: AuthStatus
  signup: (request: SignupRequest) => Promise<void>
  login: (request: LoginRequest) => Promise<void>
  logout: () => Promise<void>
  /**
   * ログイン中ユーザーの情報を差し替える。
   *
   * プロフィール更新の API が更新後の UserResponse を返すので、それをそのまま
   * 流し込めばヘッダーや投稿フォームのアバターが即座に揃う（/me の取り直しは不要）。
   */
  setCurrentUser: (user: UserResponse) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
