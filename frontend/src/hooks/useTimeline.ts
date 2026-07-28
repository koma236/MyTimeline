import { useCallback, useEffect, useRef, useState } from 'react'
import { toApiError } from '../api/client'
import * as postsApi from '../api/posts'
import type { PostResponse, TimelineTab } from '../types/post'

/**
 * タイムラインの取得とページングを引き受けるフック。
 *
 * ページングはカーソル方式（F02）。読んでいる間に新しい投稿が増えても
 * 境界がずれないよう、オフセットではなく「最後に見た投稿の id」で辿る。
 */
export function useTimeline(tab: TimelineTab) {
  const [posts, setPosts] = useState<PostResponse[]>([])
  const [nextCursor, setNextCursor] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | undefined>()

  /**
   * 直近で要求した「タブ＋カーソル」。同じものを二度取りに行かないための番人。
   *
   * 無限スクロールの番兵は同じ位置で何度も発火しうるし、React StrictMode は
   * 開発時に effect を 2 回実行する。loading は state なので同一レンダー内の
   * 連続呼び出しを止められず、ref で持つ必要がある。
   *
   * 併せて「最後に要求したのは自分か」の判定にも使う。タブを素早く切り替えると
   * 切替前のリクエストが後から返ることがあり、そのまま反映すると別タブの投稿が
   * 一覧に入り込んでしまうため、応答を受け取った時点で自分が最新か確認する。
   */
  const requestedKey = useRef<string | null>(null)

  const load = useCallback(
    async (cursor: number | null) => {
      const key = `${tab}:${cursor ?? 'head'}`
      if (requestedKey.current === key) return
      requestedKey.current = key

      setLoading(true)
      setError(undefined)
      try {
        const response = await postsApi.fetchTimeline(tab, cursor)
        // 待っている間に別のタブ・カーソルが要求されていたら、この応答は捨てる
        if (requestedKey.current !== key) return
        // 1 ページ目は置き換え、2 ページ目以降は末尾に足す
        setPosts((current) => (cursor == null ? response.posts : [...current, ...response.posts]))
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
    [tab],
  )

  // タブが変わったら 1 ページ目から取り直す。
  // requestedKey はここでは触らない（キーにタブを含めてあるので新しいタブなら必ず通り、
  // StrictMode による 2 回目の実行は同じキーとして弾かれる）
  useEffect(() => {
    setPosts([])
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

  /** 作成した投稿を先頭に差し込む。 */
  const prependPost = useCallback((post: PostResponse) => {
    setPosts((current) => [post, ...current])
  }, [])

  /** 編集後の投稿で置き換える。 */
  const replacePost = useCallback((post: PostResponse) => {
    setPosts((current) => current.map((item) => (item.id === post.id ? post : item)))
  }, [])

  /**
   * 投稿の一部だけを差し替える。
   *
   * いいねの API は投稿全体ではなく { likeCount, likedByMe } しか返さないので、
   * 完全な PostResponse を要求する replacePost は使えない。
   */
  const patchPost = useCallback((id: number, patch: Partial<PostResponse>) => {
    setPosts((current) => current.map((item) => (item.id === id ? { ...item, ...patch } : item)))
  }, [])

  /** 削除した投稿を取り除く。 */
  const removePost = useCallback((id: number) => {
    setPosts((current) => current.filter((item) => item.id !== id))
  }, [])

  return {
    posts,
    loading,
    error,
    hasMore: nextCursor != null,
    loadMore,
    retry,
    prependPost,
    replacePost,
    patchPost,
    removePost,
  }
}
