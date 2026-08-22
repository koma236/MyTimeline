import { render } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockIntersectionObserver } from '../test/intersectionObserver'
import { InfiniteScrollSentinel } from './InfiniteScrollSentinel'

/**
 * 設計技法: 状態遷移（非交差 → 交差）+ エラー推測（アンマウント後の監視解除、最新の onVisible を使う）。
 *
 * カバレッジ上は `if (!target) return`（ref が未設定のときの防御）が未到達のまま。
 * ref は同じコンポーネントの div に必ず付くため effect 実行時に null にはならず、
 * テストから再現する手段がない。到達不能な防御コードとして許容する。
 */
describe('InfiniteScrollSentinel', () => {
  let io: ReturnType<typeof mockIntersectionObserver>

  beforeEach(() => {
    io = mockIntersectionObserver()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('描画時に番兵要素を observe し、交差したら onVisible を呼ぶ', () => {
    const onVisible = vi.fn()
    render(<InfiniteScrollSentinel onVisible={onVisible} />)

    expect(io.instances).toHaveLength(1)
    expect(io.instances[0]?.observe).toHaveBeenCalledTimes(1)

    io.intersect(true)

    expect(onVisible).toHaveBeenCalledTimes(1)
  })

  it('交差していない通知では呼ばない', () => {
    const onVisible = vi.fn()
    render(<InfiniteScrollSentinel onVisible={onVisible} />)

    io.intersect(false)

    expect(onVisible).not.toHaveBeenCalled()
  })

  it('onVisible が差し替わっても observer を張り直さず、最新の関数を呼ぶ', () => {
    const first = vi.fn()
    const second = vi.fn()
    const { rerender } = render(<InfiniteScrollSentinel onVisible={first} />)

    rerender(<InfiniteScrollSentinel onVisible={second} />)
    io.intersect(true)

    expect(io.instances).toHaveLength(1)
    expect(first).not.toHaveBeenCalled()
    expect(second).toHaveBeenCalledTimes(1)
  })

  it('アンマウントで disconnect する（画面を離れた後に読み込みが走らない）', () => {
    const { unmount } = render(<InfiniteScrollSentinel onVisible={() => {}} />)

    unmount()

    expect(io.instances[0]?.disconnect).toHaveBeenCalledTimes(1)
  })
})
