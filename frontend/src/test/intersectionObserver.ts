import { vi } from 'vitest'

/** InfiniteScrollSentinel が張る observer の代役。交差イベントをテストから流せる */
class FakeIntersectionObserver {
  readonly root = null
  readonly rootMargin = ''
  readonly thresholds: ReadonlyArray<number> = []
  readonly observe = vi.fn()
  readonly unobserve = vi.fn()
  readonly disconnect = vi.fn()
  readonly takeRecords = (): IntersectionObserverEntry[] => []
  readonly callback: IntersectionObserverCallback

  constructor(callback: IntersectionObserverCallback, instances: FakeIntersectionObserver[]) {
    this.callback = callback
    instances.push(this)
  }
}

/**
 * jsdom に無い IntersectionObserver の差し替え。
 * InfiniteScrollSentinel が張った observer を手動で「交差した」ことにできる。
 */
export function mockIntersectionObserver() {
  const instances: FakeIntersectionObserver[] = []

  vi.stubGlobal(
    'IntersectionObserver',
    class extends FakeIntersectionObserver {
      constructor(callback: IntersectionObserverCallback) {
        super(callback, instances)
      }
    },
  )

  return {
    instances,
    /** 直近に作られた observer に交差イベントを流す */
    intersect(isIntersecting = true) {
      const latest = instances.at(-1)
      if (!latest) throw new Error('IntersectionObserver がまだ作られていません')
      latest.callback(
        [{ isIntersecting } as IntersectionObserverEntry],
        latest as unknown as IntersectionObserver,
      )
    },
  }
}
