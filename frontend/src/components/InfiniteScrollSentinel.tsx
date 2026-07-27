import { useEffect, useRef } from 'react'

interface InfiniteScrollSentinelProps {
  /** 番兵が画面に入ったときに呼ばれる。多重呼び出しの抑止は呼び出し側の責務 */
  onVisible: () => void
}

/**
 * リスト末尾に置く番兵。画面に入ったら次のページを要求する。
 *
 * scroll イベントを間引くより IntersectionObserver の方が単純で、
 * 「あと何 px で最下部か」をレイアウトから計算せずに済む。
 *
 * rootMargin を広げてあるのは、実際に最下部へ着く手前で読み始めて
 * 読み込み待ちを目立たせないため。
 */
export function InfiniteScrollSentinel({ onVisible }: InfiniteScrollSentinelProps) {
  const ref = useRef<HTMLDivElement>(null)

  // onVisible は毎レンダー新しい関数になりうるので、
  // observer を張り直さずに済むよう ref 経由で最新版を呼ぶ
  const onVisibleRef = useRef(onVisible)
  useEffect(() => {
    onVisibleRef.current = onVisible
  }, [onVisible])

  useEffect(() => {
    const target = ref.current
    if (!target) return

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) onVisibleRef.current()
      },
      { rootMargin: '400px' },
    )
    observer.observe(target)

    return () => observer.disconnect()
  }, [])

  return <div ref={ref} aria-hidden="true" className="h-px" />
}
