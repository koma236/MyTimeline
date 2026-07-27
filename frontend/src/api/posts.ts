import { apiClient } from './client'
import type {
  PostRequest,
  PostResponse,
  TimelineResponse,
  TimelineTab,
} from '../types/post'

/**
 * 投稿・タイムライン API。
 *
 * アクセストークンの付与と 401 時の自動リフレッシュは client.ts の
 * インターセプタが行うので、ここでは認証を意識しなくてよい。
 */

/** 投稿を作成する（F03 2. 機能詳細）。 */
export async function createPost(request: PostRequest): Promise<PostResponse> {
  const response = await apiClient.post<PostResponse>('/posts', request)
  return response.data
}

export async function fetchPost(id: number): Promise<PostResponse> {
  const response = await apiClient.get<PostResponse>(`/posts/${id}`)
  return response.data
}

/** 本文を編集する。自分の投稿でなければ 403 が返る。 */
export async function updatePost(id: number, request: PostRequest): Promise<PostResponse> {
  const response = await apiClient.put<PostResponse>(`/posts/${id}`, request)
  return response.data
}

/** 投稿を削除する。自分の投稿でなければ 403 が返る。 */
export async function deletePost(id: number): Promise<void> {
  await apiClient.delete(`/posts/${id}`)
}

/**
 * タイムラインを 1 ページ分取得する（F02）。
 *
 * cursor は前回のレスポンスの nextCursor をそのまま渡す。省略すると最新から返る。
 */
export async function fetchTimeline(
  tab: TimelineTab,
  cursor?: number | null,
): Promise<TimelineResponse> {
  const response = await apiClient.get<TimelineResponse>(`/timeline/${tab}`, {
    params: cursor == null ? undefined : { cursor },
  })
  return response.data
}
