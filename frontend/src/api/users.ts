import { apiClient } from './client'
import type { UserResponse } from '../types/auth'
import type { TimelineResponse } from '../types/post'
import type { ProfileResponse, UpdateProfileRequest } from '../types/user'

/**
 * プロフィール API（F07）。
 *
 * アクセストークンの付与と 401 時の自動リフレッシュは client.ts の
 * インターセプタが行うので、ここでは認証を意識しなくてよい。
 */

/** username でプロフィールを取得する（SCR-05）。存在しなければ 404。 */
export async function fetchProfile(username: string): Promise<ProfileResponse> {
  const response = await apiClient.get<ProfileResponse>(`/users/${encodeURIComponent(username)}`)
  return response.data
}

/**
 * 対象ユーザーの投稿を 1 ページ分取得する。
 *
 * タイムラインと同じ形（TimelineResponse）で返るので、表示側は使い回せる。
 */
export async function fetchUserPosts(
  username: string,
  cursor?: number | null,
): Promise<TimelineResponse> {
  const response = await apiClient.get<TimelineResponse>(
    `/users/${encodeURIComponent(username)}/posts`,
    { params: cursor == null ? undefined : { cursor } },
  )
  return response.data
}

/** 表示名と自己紹介を更新する。更新後のログインユーザーが返る。 */
export async function updateProfile(request: UpdateProfileRequest): Promise<UserResponse> {
  const response = await apiClient.put<UserResponse>('/users/me', request)
  return response.data
}

/**
 * アバター画像を差し替える。
 *
 * Content-Type を自分で指定しないこと。FormData を渡せばブラウザが
 * multipart の boundary 付きで設定してくれるが、手で書くと boundary が抜けて壊れる。
 */
export async function uploadAvatar(file: File): Promise<UserResponse> {
  const form = new FormData()
  form.append('file', file)
  const response = await apiClient.put<UserResponse>('/users/me/avatar', form)
  return response.data
}

/** アバター画像を外して初期アバターに戻す。未設定でもエラーにはならない。 */
export async function deleteAvatar(): Promise<UserResponse> {
  const response = await apiClient.delete<UserResponse>('/users/me/avatar')
  return response.data
}
