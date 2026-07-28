import { useCallback, useEffect, useRef, useState } from 'react'
import { toApiError } from '../api/client'
import * as commentsApi from '../api/comments'
import type { CommentResponse } from '../types/comment'

/**
 * 投稿 1 件分のコメントの取得とページングを引き受けるフック。
 *
 * useTimeline と同じカーソル方式だが、コメントは古い順に並ぶ（会話は上から下へ
 * 読むもので、後から来たコメントで既読部分の位置がずれない方がよい）。
 * そのためカーソルは「最後に読んだコメントの id」より新しいものを指す。
 */
export function useComments(postId: number) {
  const [comments, setComments] = useState<CommentResponse[]>([])
  const [nextCursor, setNextCursor] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | undefined>()

  /**
   * 直近で要求した「投稿＋カーソル」。同じものを二度取りに行かないための番人。
   *
   * useTimeline と同じ理由で ref にしている。無限スクロールの番兵は同じ位置で
   * 何度も発火しうるし、React StrictMode は開発時に effect を 2 回実行する。
   * loading は state なので同一レンダー内の連続呼び出しを止められない。
   */
  const requestedKey = useRef<string | null>(null)

  const load = useCallback(
    async (cursor: number | null) => {
      const key = `${postId}:${cursor ?? 'head'}`
      if (requestedKey.current === key) return
      requestedKey.current = key

      setLoading(true)
      setError(undefined)
      try {
        const response = await commentsApi.fetchComments(postId, cursor)
        // 待っている間に別の投稿・カーソルが要求されていたら、この応答は捨てる
        if (requestedKey.current !== key) return
        // 古い順なので、1 ページ目は置き換え、2 ページ目以降は末尾に足す
        setComments((current) =>
          cursor == null ? response.comments : [...current, ...response.comments],
        )
        setNextCursor(response.nextCursor)
      } catch (caught) {
        if (requestedKey.current !== key) return
        setError(toApiError(caught).message)
        // 失敗した要求は再試行できるようにしておく
        requestedKey.current = null
      } finally {
        // 追い越された要求は loading を戻さない（最新の要求がまだ読み込み中のため）
        if (requestedKey.current === key || requestedKey.current === null) {
          setLoading(false)
        }
      }
    },
    [postId],
  )

  useEffect(() => {
    setComments([])
    setNextCursor(null)
    void load(null)
  }, [load])

  /** 次のページを読む。末尾に達しているか取得中なら何もしない。 */
  const loadMore = useCallback(() => {
    if (nextCursor == null || loading) return
    void load(nextCursor)
  }, [load, loading, nextCursor])

  /** 取得に失敗したときの再試行。 */
  const retry = useCallback(() => {
    requestedKey.current = null
    void load(nextCursor)
  }, [load, nextCursor])

  /**
   * 投稿したコメントを末尾に足す。
   *
   * 古い順なので新しいコメントは常に一番下。読んでいる途中のページを
   * 取り直さずに済むよう、サーバーから返った 1 件だけを差し込む。
   */
  const appendComment = useCallback((comment: CommentResponse) => {
    setComments((current) => [...current, comment])
  }, [])

  /** 編集後のコメントで置き換える。 */
  const replaceComment = useCallback((comment: CommentResponse) => {
    setComments((current) => current.map((item) => (item.id === comment.id ? comment : item)))
  }, [])

  /** 削除したコメントを取り除く。 */
  const removeComment = useCallback((id: number) => {
    setComments((current) => current.filter((item) => item.id !== id))
  }, [])

  return {
    comments,
    loading,
    error,
    hasMore: nextCursor != null,
    loadMore,
    retry,
    appendComment,
    replaceComment,
    removeComment,
  }
}
