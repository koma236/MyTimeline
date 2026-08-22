import { act, renderHook, waitFor } from '@testing-library/react'
import { StrictMode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useCursorPager, type CursorPage } from './useCursorPager'

/**
 * 設計技法: 分岐網羅。useCursorPager の分岐は以下で、それぞれ真偽の両方を通す。
 *
 *   1. requestedKey.current === key          → 同じ要求の二重実行を抑止（真: StrictMode / 偽: 通常）
 *   2. 応答時 requestedKey.current !== key    → 追い越された応答を捨てる（成功時・失敗時の両方）
 *   3. cursor == null                         → 1 ページ目は置き換え / 2 ページ目以降は追記
 *   4. catch                                  → エラーを保持し、再試行できるよう key を戻す
 *   5. finally の loading 判定               → 最新の要求だけが loading を戻す
 *   6. loadMore: nextCursor == null || loading → 末尾 / 取得中は何もしない
 *
 * 加えて一覧操作（prepend / append / replace / patch / remove）は「対象あり / 対象なし」の同値分割で見る。
 */
interface Item {
  id: number
  name: string
}

function item(id: number): Item {
  return { id, name: `item ${id}` }
}

function page(ids: number[], nextCursor: number | null = null): CursorPage<Item> {
  return { items: ids.map(item), nextCursor }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((settle, fail) => {
    resolve = settle
    reject = fail
  })
  return { promise, resolve, reject }
}

const ids = (items: Item[]) => items.map((entry) => entry.id)

describe('useCursorPager', () => {
  let fetchPage: ReturnType<typeof vi.fn<(cursor: number | null) => Promise<CursorPage<Item>>>>

  function renderPager(key = 'a', options?: { strict?: boolean }) {
    return renderHook(({ requestKey }: { requestKey: string }) => useCursorPager<Item>(requestKey, fetchPage), {
      initialProps: { requestKey: key },
      wrapper: options?.strict ? StrictMode : undefined,
    })
  }

  beforeEach(() => {
    fetchPage = vi.fn()
  })

  describe('取得とページング', () => {
    it('分岐 3（cursor == null）: 初回は cursor=null で取得し、結果で置き換える', async () => {
      fetchPage.mockResolvedValue(page([3, 2], 2))

      const { result } = renderPager()

      expect(result.current.loading).toBe(true)
      await waitFor(() => expect(result.current.loading).toBe(false))
      expect(ids(result.current.items)).toEqual([3, 2])
      expect(result.current.hasMore).toBe(true)
      expect(result.current.error).toBeUndefined()
      expect(fetchPage).toHaveBeenCalledWith(null)
    })

    it('分岐 3（cursor != null）: loadMore は nextCursor を渡し、結果を末尾に足す', async () => {
      fetchPage.mockResolvedValueOnce(page([3, 2], 2)).mockResolvedValueOnce(page([1]))

      const { result } = renderPager()
      await waitFor(() => expect(result.current.loading).toBe(false))

      act(() => result.current.loadMore())

      await waitFor(() => expect(result.current.items).toHaveLength(3))
      expect(ids(result.current.items)).toEqual([3, 2, 1])
      expect(fetchPage).toHaveBeenLastCalledWith(2)
      expect(result.current.hasMore).toBe(false)
    })

    it('分岐 6（nextCursor == null）: 末尾まで読んだら loadMore は何もしない', async () => {
      fetchPage.mockResolvedValue(page([1]))

      const { result } = renderPager()
      await waitFor(() => expect(result.current.loading).toBe(false))

      act(() => result.current.loadMore())

      expect(fetchPage).toHaveBeenCalledTimes(1)
    })

    it('分岐 6（loading）: 取得中に loadMore を重ねても二重に取りに行かない', async () => {
      const second = deferred<CursorPage<Item>>()
      fetchPage.mockResolvedValueOnce(page([3], 3)).mockReturnValueOnce(second.promise)

      const { result } = renderPager()
      await waitFor(() => expect(result.current.loading).toBe(false))

      act(() => result.current.loadMore())
      act(() => result.current.loadMore())

      expect(fetchPage).toHaveBeenCalledTimes(2)
      await act(async () => second.resolve(page([2])))
      expect(ids(result.current.items)).toEqual([3, 2])
    })

    it('分岐 1: StrictMode で effect が 2 回走っても同じ要求は 1 回しか取りに行かない', async () => {
      fetchPage.mockResolvedValue(page([1]))

      const { result } = renderPager('a', { strict: true })

      await waitFor(() => expect(result.current.loading).toBe(false))
      expect(fetchPage).toHaveBeenCalledTimes(1)
    })

    it('requestKey が変わったら一覧を空にして 1 ページ目から取り直す', async () => {
      fetchPage.mockResolvedValueOnce(page([3, 2], 2)).mockResolvedValueOnce(page([9]))

      const { result, rerender } = renderPager('a')
      await waitFor(() => expect(result.current.loading).toBe(false))

      rerender({ requestKey: 'b' })

      await waitFor(() => expect(ids(result.current.items)).toEqual([9]))
      expect(fetchPage).toHaveBeenLastCalledWith(null)
      expect(result.current.hasMore).toBe(false)
    })
  })

  describe('追い越された応答', () => {
    it('分岐 2（成功）: 切替前の要求が後から返っても反映せず、新しい対象の結果だけが残る', async () => {
      const first = deferred<CursorPage<Item>>()
      const second = deferred<CursorPage<Item>>()
      fetchPage.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

      const { result, rerender } = renderPager('a')
      rerender({ requestKey: 'b' })

      await act(async () => second.resolve(page([9])))
      expect(ids(result.current.items)).toEqual([9])
      expect(result.current.loading).toBe(false)

      await act(async () => first.resolve(page([1, 2])))
      expect(ids(result.current.items)).toEqual([9])
      expect(result.current.loading).toBe(false)
    })

    it('分岐 2（失敗）: 切替前の要求が後から失敗しても、エラーとして扱わない', async () => {
      const first = deferred<CursorPage<Item>>()
      const second = deferred<CursorPage<Item>>()
      fetchPage.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

      const { result, rerender } = renderPager('a')
      rerender({ requestKey: 'b' })
      await act(async () => second.resolve(page([9])))

      await act(async () => first.reject(new Error('stale failure')))

      expect(result.current.error).toBeUndefined()
      expect(ids(result.current.items)).toEqual([9])
    })

    it('分岐 5: 追い越された要求の完了では loading を戻さない（最新の要求がまだ読み込み中）', async () => {
      const first = deferred<CursorPage<Item>>()
      const second = deferred<CursorPage<Item>>()
      fetchPage.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

      const { result, rerender } = renderPager('a')
      rerender({ requestKey: 'b' })

      await act(async () => first.resolve(page([1])))

      expect(result.current.loading).toBe(true)
      await act(async () => second.resolve(page([9])))
      expect(result.current.loading).toBe(false)
    })
  })

  describe('エラーと再試行', () => {
    it('分岐 4: 取得に失敗したらエラーを保持し、一覧は空のまま loading を戻す', async () => {
      fetchPage.mockRejectedValue(new Error('boom'))

      const { result } = renderPager()

      await waitFor(() => expect(result.current.error).toBeDefined())
      expect(result.current.loading).toBe(false)
      expect(result.current.items).toEqual([])
    })

    it('retry は同じカーソルで取り直し、成功したらエラーが消える', async () => {
      fetchPage.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(page([1]))

      const { result } = renderPager()
      await waitFor(() => expect(result.current.error).toBeDefined())

      act(() => result.current.retry())

      await waitFor(() => expect(ids(result.current.items)).toEqual([1]))
      expect(result.current.error).toBeUndefined()
      expect(fetchPage).toHaveBeenCalledTimes(2)
      expect(fetchPage).toHaveBeenLastCalledWith(null)
    })

    it('2 ページ目の取得に失敗した場合、retry はそのページのカーソルで取り直し、1 ページ目は保持する', async () => {
      fetchPage
        .mockResolvedValueOnce(page([3, 2], 2))
        .mockRejectedValueOnce(new Error('boom'))
        .mockResolvedValueOnce(page([1]))

      const { result } = renderPager()
      await waitFor(() => expect(result.current.loading).toBe(false))
      act(() => result.current.loadMore())
      await waitFor(() => expect(result.current.error).toBeDefined())
      expect(ids(result.current.items)).toEqual([3, 2])

      act(() => result.current.retry())

      await waitFor(() => expect(ids(result.current.items)).toEqual([3, 2, 1]))
      expect(fetchPage).toHaveBeenLastCalledWith(2)
    })
  })

  describe('一覧の部分更新', () => {
    async function renderLoaded() {
      fetchPage.mockResolvedValue(page([3, 2, 1]))
      const rendered = renderPager()
      await waitFor(() => expect(rendered.result.current.loading).toBe(false))
      return rendered
    }

    it('prependItem は先頭に、appendItem は末尾に足す', async () => {
      const { result } = await renderLoaded()

      act(() => result.current.prependItem(item(4)))
      act(() => result.current.appendItem(item(0)))

      expect(ids(result.current.items)).toEqual([4, 3, 2, 1, 0])
    })

    it('replaceItem は同じ id の要素を丸ごと置き換え、位置は変えない', async () => {
      const { result } = await renderLoaded()

      act(() => result.current.replaceItem({ id: 2, name: 'edited' }))

      expect(result.current.items[1]).toEqual({ id: 2, name: 'edited' })
      expect(ids(result.current.items)).toEqual([3, 2, 1])
    })

    it('patchItem は一部のフィールドだけを差し替える', async () => {
      const { result } = await renderLoaded()

      act(() => result.current.patchItem(3, { name: 'patched' }))

      expect(result.current.items[0]).toEqual({ id: 3, name: 'patched' })
    })

    it('removeItem は該当 id だけを取り除く', async () => {
      const { result } = await renderLoaded()

      act(() => result.current.removeItem(2))

      expect(ids(result.current.items)).toEqual([3, 1])
    })

    it('同値分割（対象なし）: 存在しない id への replace / patch / remove は一覧を変えない', async () => {
      const { result } = await renderLoaded()

      act(() => result.current.replaceItem(item(99)))
      act(() => result.current.patchItem(99, { name: 'x' }))
      act(() => result.current.removeItem(99))

      expect(result.current.items).toEqual([item(3), item(2), item(1)])
    })
  })
})
