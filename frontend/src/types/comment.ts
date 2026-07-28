/**
 * コメント API の型定義。バックエンドの DTO
 * (backend/src/main/java/com/example/mytimeline/dto) と 1:1 で対応させる。
 */

import type { PostAuthor } from './post'

export interface CommentResponse {
  id: number
  postId: number
  body: string
  /** 投稿と同じ形なので PostAuthor を使い回す */
  author: PostAuthor
  /** LocalDateTime のためタイムゾーンを持たない ISO 文字列 */
  createdAt: string
  updatedAt: string
}

/** コメントの作成・編集で送る内容。どちらも本文だけなので共用する */
export interface CommentRequest {
  body: string
}

export interface CommentListResponse {
  comments: CommentResponse[]
  /**
   * 次ページのカーソル。以降が無ければ null。
   *
   * タイムラインと違いコメントは古い順なので、続きは「このカーソルより新しいもの」になる。
   */
  nextCursor: number | null
}
