/**
 * 投稿・タイムライン API の型定義。バックエンドの DTO
 * (backend/src/main/java/com/example/mytimeline/dto) と 1:1 で対応させる。
 */

/** 投稿に埋め込まれる投稿者。UserResponse と違いメールアドレスを含まない */
export interface PostAuthor {
  id: number
  username: string
  displayName: string
  /** アバターの閲覧用 URL。未設定なら null（イニシャルの初期アバターを表示する） */
  avatarUrl: string | null
}

export interface PostResponse {
  id: number
  body: string
  author: PostAuthor
  /** いいね数（F05） */
  likeCount: number
  /** コメント数（F04） */
  commentCount: number
  /**
   * ログインユーザーがこの投稿にいいね済みか。
   *
   * 「自分の投稿か」は author.id との比較で分かるが、これは分からないのでサーバーが返す。
   */
  likedByMe: boolean
  /** LocalDateTime のためタイムゾーンを持たない ISO 文字列 */
  createdAt: string
  updatedAt: string
}

/** いいねの付与・取り消し後の状態（POST / DELETE /api/posts/{id}/like のレスポンス） */
export interface LikeResponse {
  likeCount: number
  likedByMe: boolean
}

/** 投稿の作成・編集で送る内容。どちらも本文だけなので共用する */
export interface PostRequest {
  body: string
}

export interface TimelineResponse {
  posts: PostResponse[]
  /** 次ページのカーソル。以降が無ければ null（@JsonInclude ではないので必ずキーは存在する） */
  nextCursor: number | null
}

/** タイムラインのタブ。API のパス（/api/timeline/{tab}）と一致させている */
export type TimelineTab = 'following' | 'all'
