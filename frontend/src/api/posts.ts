import { apiClient } from './client'
import type {
  LikeResponse,
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

/**
 * 投稿を作成する（F03 2. 機能詳細）。
 *
 * 画像を添付できるように multipart で送る（画像なしでも同じ形式）。
 * 画像の検証（形式・サイズ・枚数）はサーバー側が行い、拒否理由は
 * fieldErrors.image で返る。
 */
export async function createPost(body: string, images: File[] = []): Promise<PostResponse> {
  const formData = new FormData()
  formData.append('body', body)
  for (const image of images) {
    formData.append('images', image)
  }
  const response = await apiClient.post<PostResponse>('/posts', formData)
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

/**
 * いいねを付ける（F05）。
 *
 * トグルではなく付与と取り消しで分かれているので、何度呼んでも結果は同じ（冪等）。
 * 戻り値の likeCount / likedByMe をそのままボタンの表示に使える。
 */
export async function likePost(id: number): Promise<LikeResponse> {
  const response = await apiClient.post<LikeResponse>(`/posts/${id}/like`)
  return response.data
}

/** いいねを取り消す。付いていない状態で呼んでもエラーにはならない。 */
export async function unlikePost(id: number): Promise<LikeResponse> {
  const response = await apiClient.delete<LikeResponse>(`/posts/${id}/like`)
  return response.data
}
