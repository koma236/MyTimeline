import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiError, AuthResponse } from '../types/auth'

/**
 * バックエンドへの HTTP クライアント。
 *
 * dev は Vite のプロキシ、本番は CloudFront が /api/* をバックエンドへ流すため、
 * どちらも同一オリジンの相対パスで済む。
 */
export const apiClient = axios.create({
  baseURL: '/api',
  // リフレッシュトークンの httpOnly Cookie を送受信するために必須
  withCredentials: true,
})

/**
 * アクセストークンはメモリだけで保持し、localStorage には書かない。
 *
 * 永続化はリフレッシュトークンの Cookie が担うので保存する必要がなく、
 * localStorage に置くと XSS で読み出せてしまうため。
 * リロードすると消えるが、起動時の refresh で取り直せる。
 */
let accessToken: string | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

/** リフレッシュにも失敗し、ログイン状態を維持できなくなったときに呼ばれる。 */
let onSessionExpired: (() => void) | null = null

export function setOnSessionExpired(handler: (() => void) | null): void {
  onSessionExpired = handler
}

apiClient.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

/**
 * 401 を受けても再取得を試みないエンドポイント。
 *
 * ログイン・新規登録の 401 は「認証情報が違う」という結果そのものなので、
 * リフレッシュして再送すると画面にエラーを出せなくなる。
 * refresh 自身の 401 で再帰しないためにも除外が必要。
 */
const NO_RETRY_PATHS = ['/auth/login', '/auth/signup', '/auth/refresh', '/auth/logout']

function isNoRetryPath(url: string | undefined): boolean {
  return NO_RETRY_PATHS.some((path) => url?.includes(path))
}

/**
 * 進行中のリフレッシュ。同時に複数の 401 が起きても 1 本にまとめる。
 *
 * バックエンドはリフレッシュトークンをローテーションし、使用済みトークンの再提示を
 * 盗用とみなして<b>全セッションを失効させる</b>。並行して 2 回 refresh を投げると
 * 2 本目が古いトークンを使うことになり、正規の利用者が強制ログアウトされてしまう。
 * React StrictMode が開発時に効果を 2 回実行する対策も兼ねている。
 */
let refreshPromise: Promise<AuthResponse> | null = null

export function refreshSession(): Promise<AuthResponse> {
  if (!refreshPromise) {
    refreshPromise = apiClient
      .post<AuthResponse>('/auth/refresh')
      .then((response) => {
        setAccessToken(response.data.accessToken)
        return response.data
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetriableConfig | undefined

    if (
      error.response?.status !== 401 ||
      !original ||
      original._retried ||
      isNoRetryPath(original.url)
    ) {
      return Promise.reject(error)
    }

    // アクセストークンの期限切れとみなし、取り直して 1 度だけ再送する
    original._retried = true
    try {
      const refreshed = await refreshSession()
      original.headers.Authorization = `Bearer ${refreshed.accessToken}`
      return await apiClient(original)
    } catch {
      // リフレッシュトークンも無効。ログイン画面へ戻すしかない
      setAccessToken(null)
      onSessionExpired?.()
      return Promise.reject(error)
    }
  },
)

/**
 * 例外を画面で扱える形に正規化する。
 *
 * バリデーションエラーのメッセージはバックエンドが日本語で返すため、
 * フロント側では文言を持たずそのまま表示する（二重管理を避ける）。
 */
export function toApiError(error: unknown): ApiError {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as ApiError | undefined
    if (data?.message) {
      return { message: data.message, fieldErrors: data.fieldErrors }
    }
    if (!error.response) {
      return { message: 'サーバーに接続できませんでした。時間をおいて再度お試しください' }
    }
  }
  return { message: '予期しないエラーが発生しました' }
}
