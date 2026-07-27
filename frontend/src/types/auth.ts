/**
 * 認証 API の型定義。バックエンドの DTO
 * (backend/src/main/java/com/example/mytimeline/dto) と 1:1 で対応させる。
 */

export interface UserResponse {
  id: number
  username: string
  displayName: string
  email: string
  /** 未設定なら null。signup 直後は必ず null */
  bio: string | null
  /** LocalDateTime のためタイムゾーンを持たない ISO 文字列（例: 2026-07-27T12:34:56.789） */
  createdAt: string
}

export interface SignupRequest {
  username: string
  displayName: string
  email: string
  password: string
}

export interface LoginRequest {
  /** メールアドレスとユーザー名のどちらでもよい（SCR-01 の入力欄が兼ねているため） */
  identifier: string
  password: string
}

/**
 * signup / login / refresh の成功レスポンス。
 *
 * リフレッシュトークンは httpOnly Cookie で返るため、ここには含まれない。
 */
export interface AuthResponse {
  accessToken: string
  user: UserResponse
}

/**
 * エラーレスポンス。バックエンドの ErrorResponse に対応する。
 *
 * fieldErrors は @JsonInclude(NON_NULL) のため、項目エラーが無い場合はキーごと存在しない。
 */
export interface ApiError {
  message: string
  fieldErrors?: Record<string, string>
}
