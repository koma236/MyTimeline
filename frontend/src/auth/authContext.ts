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
}

export const AuthContext = createContext<AuthContextValue | null>(null)
