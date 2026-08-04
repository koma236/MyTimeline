import { useCallback, useEffect, useRef, useState } from 'react'
import { toApiError } from '../api/client'

/** 1 ページ分の取得結果。カーソルページングの API はすべてこの形で返る */
export interface CursorPage<T> {
  items: T[]
  /** 次ページのカーソル。以降が無ければ null */
  nextCursor: number | null
}

/**
 * カーソルページングの取得・追加読み込み・一覧の部分更新をまとめて引き受けるフック。
 *
 * ページングはカーソル方式（F02）。読んでいる間に新しい要素が増えても境界がずれないよう、
 * オフセットではなく「最後に見た要素の id」で辿る。
 *
 * このフック自体は「何を取ってくるか」を知らない。タイムライン・特定ユーザーの投稿など、
 * 取得先ごとに useTimeline / useUserPosts が fetchPage を渡して使う。
 * 追い越したレスポンスの破棄と二重取得の抑止（下記 requestedKey）は画面上では
 * 気付きにくい割に壊れると実害が大きいので、取得先ごとに書き写さず 1 箇所に集約している。
 *
 * @param requestKey 取得対象を表す文字列。これが変わると 1 ページ目から取り直す。
 *                   プリミティブなので、依存配列に入れても毎レンダー変わる心配がない
 * @param fetchPage  1 ページ分を取りに行く関数
 */
export function useCursorPager<T extends { id: number }>(
  requestKey: string,
  fetchPage: (cursor: number | null) => Promise<CursorPage<T>>,
) {
  const [items, setItems] = useState<T[]>([])
  const [nextCursor, setNextCursor] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | undefined>()

  /**
   * fetchPage は呼び出し側で毎レンダー作り直されることが多い。
   * 依存配列に直接入れると取得の effect が張り直され、無限に取りに行ってしまうため、
   * ref に最新版を保持して呼ぶときだけ読む。
   */
  const fetchPageRef = useRef(fetchPage)
  fetchPageRef.current = fetchPage

  /**
   * 直近で要求した「対象＋カーソル」。同じものを二度取りに行かないための番人。
   *
   * 無限スクロールの番兵は同じ位置で何度も発火しうるし、React StrictMode は
   * 開発時に effect を 2 回実行する。loading は state なので同一レンダー内の
   * 連続呼び出しを止められず、ref で持つ必要がある。
   *
   * 併せて「最後に要求したのは自分か」の判定にも使う。対象を素早く切り替えると
   * 切替前のリクエストが後から返ることがあり、そのまま反映すると別の対象の要素が
   * 一覧に入り込んでしまうため、応答を受け取った時点で自分が最新か確認する。
   */
  const requestedKey = useRef<string | null>(null)

  const load = useCallback(
    async (cursor: number | null) => {
      const key = `${requestKey}:${cursor ?? 'head'}`
      if (requestedKey.current === key) return
      requestedKey.current = key

      setLoading(true)
      setError(undefined)
      try {
        const page = await fetchPageRef.current(cursor)
        // 待っている間に別の対象・カーソルが要求されていたら、この応答は捨てる
        if (requestedKey.current !== key) return
        // 1 ページ目は置き換え、2 ページ目以降は末尾に足す
        setItems((current) => (cursor == null ? page.items : [...current, ...page.items]))
        setNextCursor(page.nextCursor)
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
    [requestKey],
  )

  // 対象が変わったら 1 ページ目から取り直す。
  // requestedKey はここでは触らない（キーに対象を含めてあるので新しい対象なら必ず通り、
  // StrictMode による 2 回目の実行は同じキーとして弾かれる）
  useEffect(() => {
    setItems([])
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

  /** 作成した要素を先頭に差し込む。 */
  const prependItem = useCallback((item: T) => {
    setItems((current) => [item, ...current])
  }, [])

  /** 作成した要素を末尾に足す（古い順に並ぶ一覧向け）。 */
  const appendItem = useCallback((item: T) => {
    setItems((current) => [...current, item])
  }, [])

  /** 編集後の要素で置き換える。 */
  const replaceItem = useCallback((item: T) => {
    setItems((current) => current.map((entry) => (entry.id === item.id ? item : entry)))
  }, [])

  /**
   * 要素の一部だけを差し替える。
   *
   * いいねの API は投稿全体ではなく { likeCount, likedByMe } しか返さないので、
   * 完全な要素を要求する replaceItem は使えない。
   */
  const patchItem = useCallback((id: number, patch: Partial<T>) => {
    setItems((current) => current.map((entry) => (entry.id === id ? { ...entry, ...patch } : entry)))
  }, [])

  /** 削除した要素を取り除く。 */
  const removeItem = useCallback((id: number) => {
    setItems((current) => current.filter((entry) => entry.id !== id))
  }, [])

  return {
    items,
    loading,
    error,
    hasMore: nextCursor != null,
    loadMore,
    retry,
    prependItem,
    appendItem,
    replaceItem,
    patchItem,
    removeItem,
  }
}
