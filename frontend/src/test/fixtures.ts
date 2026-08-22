import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import type { AuthContextValue } from '../auth/authContext'
import type { ApiError, UserResponse } from '../types/auth'
import type { CommentResponse } from '../types/comment'
import type { PostAuthor, PostResponse } from '../types/post'
import type { ProfileResponse, UserSummary } from '../types/user'

/**
 * テスト共通のフィクスチャ。
 *
 * 「ログイン中のユーザー = alice（id 1）」「他人 = taro（id 2）」を固定し、
 * 各テストは差分だけを overrides で指定する。
 */
export const USER: UserResponse = {
  id: 1,
  username: 'alice',
  displayName: 'アリス',
  email: 'alice@example.com',
  bio: null,
  avatarUrl: null,
  createdAt: '2026-01-01T00:00:00',
}

export const ME: PostAuthor = {
  id: USER.id,
  username: USER.username,
  displayName: USER.displayName,
  avatarUrl: null,
}

export const OTHER: PostAuthor = { id: 2, username: 'taro', displayName: '山田太郎', avatarUrl: null }

export function post(overrides: Partial<PostResponse> = {}): PostResponse {
  return {
    id: 10,
    body: '投稿の本文',
    author: OTHER,
    imageUrls: [],
    likeCount: 0,
    commentCount: 0,
    likedByMe: false,
    createdAt: '2026-08-06T10:00:00',
    updatedAt: '2026-08-06T10:00:00',
    ...overrides,
  }
}

export function comment(overrides: Partial<CommentResponse> = {}): CommentResponse {
  return {
    id: 100,
    postId: 10,
    body: 'コメントの本文',
    author: OTHER,
    createdAt: '2026-08-06T11:00:00',
    updatedAt: '2026-08-06T11:00:00',
    ...overrides,
  }
}

export function profile(overrides: Partial<ProfileResponse> = {}): ProfileResponse {
  return {
    id: OTHER.id,
    username: OTHER.username,
    displayName: OTHER.displayName,
    bio: null,
    avatarUrl: null,
    createdAt: '2025-03-15T00:00:00',
    followingCount: 3,
    followerCount: 5,
    followingByMe: false,
    ...overrides,
  }
}

export function userSummary(overrides: Partial<UserSummary> = {}): UserSummary {
  return {
    id: OTHER.id,
    username: OTHER.username,
    displayName: OTHER.displayName,
    bio: null,
    avatarUrl: null,
    followingByMe: false,
    ...overrides,
  }
}

export function authValue(overrides: Partial<AuthContextValue> = {}): AuthContextValue {
  return {
    user: USER,
    status: 'authenticated',
    signup: async () => {},
    login: async () => {},
    logout: async () => {},
    setCurrentUser: () => {},
    ...overrides,
  }
}

/**
 * バックエンドの共通エラー形式を持つ axios のエラー。
 * toApiError がそのまま message / fieldErrors を取り出せる形にする。
 */
export function apiError(status: number, data: ApiError): AxiosError {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
  return new AxiosError('Request failed', AxiosError.ERR_BAD_REQUEST, config, {}, {
    data,
    status,
    statusText: '',
    headers: new AxiosHeaders(),
    config,
  })
}
