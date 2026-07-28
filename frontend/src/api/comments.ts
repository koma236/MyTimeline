import { apiClient } from './client'
import type { CommentListResponse, CommentRequest, CommentResponse } from '../types/comment'

/**
 * コメント API（F04）。
 *
 * アクセストークンの付与と 401 時の自動リフレッシュは client.ts の
 * インターセプタが行うので、ここでは認証を意識しなくてよい。
 */

/**
 * 投稿のコメントを 1 ページ分取得する。
 *
 * cursor は前回のレスポンスの nextCursor をそのまま渡す。省略すると最古から返る。
 */
export async function fetchComments(
  postId: number,
  cursor?: number | null,
): Promise<CommentListResponse> {
  const response = await apiClient.get<CommentListResponse>(`/posts/${postId}/comments`, {
    params: cursor == null ? undefined : { cursor },
  })
  return response.data
}

export async function createComment(
  postId: number,
  request: CommentRequest,
): Promise<CommentResponse> {
  const response = await apiClient.post<CommentResponse>(`/posts/${postId}/comments`, request)
  return response.data
}

/** 本文を編集する。自分のコメントでなければ 403 が返る。 */
export async function updateComment(
  id: number,
  request: CommentRequest,
): Promise<CommentResponse> {
  const response = await apiClient.put<CommentResponse>(`/comments/${id}`, request)
  return response.data
}

/** コメントを削除する。自分のコメントでなければ 403 が返る。 */
export async function deleteComment(id: number): Promise<void> {
  await apiClient.delete(`/comments/${id}`)
}
