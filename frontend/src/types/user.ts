/**
 * プロフィール API の型定義。バックエンドの DTO
 * (backend/src/main/java/com/example/mytimeline/dto) と 1:1 で対応させる。
 */

/**
 * 他人にも見せるプロフィール（SCR-05）。
 *
 * UserResponse と違いメールアドレスを含まない。誰でも開ける画面で使うため。
 * フォロー中数・フォロワー数は F06（フォロー機能）で追加する。
 */
export interface ProfileResponse {
  id: number
  username: string
  displayName: string
  /** 未設定なら null */
  bio: string | null
  /** アバターの閲覧用 URL。未設定なら null */
  avatarUrl: string | null
  /** LocalDateTime のためタイムゾーンを持たない ISO 文字列 */
  createdAt: string
}

/**
 * プロフィール更新で送る内容。
 *
 * username とメールアドレスは変更できない。アバター画像は multipart の
 * 別エンドポイントで扱うのでここには含まれない。
 */
export interface UpdateProfileRequest {
  displayName: string
  /** 空文字を送ると未設定（null）に戻る */
  bio: string
}
