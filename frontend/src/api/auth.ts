import { apiClient, refreshSession } from './client'
import type { AuthResponse, LoginRequest, SignupRequest, UserResponse } from '../types/auth'

/** 新規登録。成功するとそのままログイン状態になる（F01 2. 機能詳細）。 */
export async function signup(request: SignupRequest): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>('/auth/signup', request)
  return response.data
}

export async function login(request: LoginRequest): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>('/auth/login', request)
  return response.data
}

/**
 * Cookie のリフレッシュトークンでアクセストークンを取り直す。
 * 同時呼び出しを 1 本にまとめる必要があるため client 側の実装を使う。
 */
export const refresh = refreshSession

/**
 * ログアウト。バックエンドがリフレッシュトークンを失効させ Cookie を削除する。
 * 失敗しても呼び出し側はローカルの状態を破棄すること。
 */
export async function logout(): Promise<void> {
  await apiClient.post('/auth/logout')
}

/** ログイン中ユーザーの取得。アクセストークンが有効であることの確認を兼ねる。 */
export async function fetchMe(): Promise<UserResponse> {
  const response = await apiClient.get<UserResponse>('/auth/me')
  return response.data
}
