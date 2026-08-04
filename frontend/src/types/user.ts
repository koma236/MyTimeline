/**
 * プロフィール API の型定義。バックエンドの DTO
 * (backend/src/main/java/com/example/mytimeline/dto) と 1:1 で対応させる。
 */

/**
 * 他人にも見せるプロフィール（SCR-05）。
 *
 * UserResponse と違いメールアドレスを含まない。誰でも開ける画面で使うため。
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
  /** このユーザーがフォローしている人数（F06） */
  followingCount: number
  /** このユーザーをフォローしている人数（F06） */
  followerCount: number
  /** ログインユーザーがこのユーザーをフォロー済みか。自分自身なら常に false */
  followingByMe: boolean
}

/**
 * ユーザー検索結果の 1 件（SCR-06・F06）。
 *
 * 検索結果カードに出す項目だけを持つ。ProfileResponse と違いフォロー中数・
 * フォロワー数を含まない（カードに出さないうえ、件数分の集計が要るため）。
 */
export interface UserSummary {
  id: number
  username: string
  displayName: string
  /** 未設定なら null */
  bio: string | null
  /** アバターの閲覧用 URL。未設定なら null */
  avatarUrl: string | null
  followingByMe: boolean
}

/** ユーザー検索の 1 ページ分。ページングはタイムラインと同じカーソル方式 */
export interface UserSearchResponse {
  users: UserSummary[]
  /** 次ページのカーソル。以降が無ければ null */
  nextCursor: number | null
}

/** フォロー / フォロー解除後の状態（POST / DELETE /api/users/{id}/follow のレスポンス） */
export interface FollowResponse {
  /** 操作後の、対象ユーザーのフォロワー数 */
  followerCount: number
  followingByMe: boolean
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
