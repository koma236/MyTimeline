// ブラウザ性能の計測ヘルパー。
//
// 数値はすべてブラウザ内（performance.now() / PerformanceObserver / CDP）で取り、
// Playwright と Node の往復をできるだけ含めない。含まれるのは「操作を起こす click の往復
// 1 回分（数十 ms）」だけで、それも毎回同じだけ乗るので前回比較には影響しない。
//
// 各操作は PERF_RUNS 回繰り返し、中央値で合否を判定する（1 回の外れ値で落とさない）。
// 最小・最大は記録だけする。合否基準は docs/14_e2e_test.md。
import { expect, type Page, type TestInfo } from '@playwright/test'
import { PERF_RUNS } from './env'

export type MetricUnit = 'ms' | 'KB' | 'MB' | 'count' | 'score'

export interface PerfMetric {
  /** レポートの行名。同じ名前は同じ行に集計される */
  name: string
  unit: MetricUnit
  values: number[]
  /** 中央値がこの値未満なら合格。無ければ記録のみ */
  threshold?: number
  note?: string
}

export function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 0 ? (sorted[mid - 1]! + sorted[mid]!) / 2 : sorted[mid]!
}

/** 計測値を添付する。perf-reporter がこれを集めて results/ に書き出す */
export async function recordMetric(testInfo: TestInfo, metric: PerfMetric): Promise<void> {
  await testInfo.attach(`perf:${metric.name}`, {
    body: JSON.stringify(metric),
    contentType: 'application/json',
  })
}

/** 操作を PERF_RUNS 回繰り返して計測し、記録して中央値を返す */
export async function measureRepeated(
  testInfo: TestInfo,
  metric: Omit<PerfMetric, 'values'>,
  run: (index: number) => Promise<number>,
): Promise<number> {
  const values: number[] = []
  for (let i = 0; i < PERF_RUNS; i += 1) {
    values.push(await run(i))
  }
  await recordMetric(testInfo, { ...metric, values })
  const result = median(values)
  // 記録してから判定する。落ちてもレポートに数値が残るように
  if (metric.threshold !== undefined) {
    expect(result, `${metric.name} の中央値（${metric.unit}）`).toBeLessThan(metric.threshold)
  }
  return result
}

/** 単発の計測値を記録し、閾値があれば判定する */
export async function recordValue(testInfo: TestInfo, metric: Omit<PerfMetric, 'values'>, value: number): Promise<void> {
  await recordMetric(testInfo, { ...metric, values: [value] })
  if (metric.threshold !== undefined) {
    expect(value, `${metric.name}（${metric.unit}）`).toBeLessThan(metric.threshold)
  }
}

/**
 * 操作の開始から、画面の条件が満たされるまでをブラウザ内の performance.now() で測る。
 *
 * until はブラウザ内で毎フレーム評価される。外側の変数は参照できないので arg で渡すこと。
 */
export async function measureInteraction<Arg>(
  page: Page,
  action: () => Promise<void>,
  until: (arg: Arg) => boolean,
  arg: Arg,
): Promise<number> {
  const start = await page.evaluate(() => performance.now())
  await action()
  const handle = await page.waitForFunction(
    ({ source, value }) => {
      // 文字列で受け取った条件をブラウザ内で関数に戻す。満たした瞬間の時刻を返す
      const predicate = new Function(`return (${source})`)() as (arg: unknown) => boolean
      return predicate(value) ? performance.now() : false
    },
    { source: until.toString(), value: arg },
    { polling: 'raf', timeout: 15_000 },
  )
  const end = (await handle.jsonValue()) as number
  return end - start
}

export interface PageLoadMetrics {
  /** サーバーの最初の 1 バイトまで。プロキシと backend の応答時間 */
  ttfbMs: number
  domContentLoadedMs: number
  loadMs: number
  /** First Contentful Paint */
  fcpMs: number
  /** Largest Contentful Paint。入力が無い間は更新され続けるので、画面が落ち着いてから読む */
  lcpMs: number
  /** Cumulative Layout Shift（入力直後 500 ms のものを除く） */
  cls: number
  jsTransferKB: number
  cssTransferKB: number
  requestCount: number
}

/**
 * 直前の page.goto についての読み込み指標を読む。呼ぶ前に画面が落ち着くまで待つこと
 * （LCP はデータ到着後に描かれた要素で更新される）。
 */
export async function readPageLoad(page: Page): Promise<PageLoadMetrics> {
  return page.evaluate(async () => {
    const observeBuffered = (type: string): Promise<PerformanceEntry[]> =>
      new Promise((resolve) => {
        const collected: PerformanceEntry[] = []
        const observer = new PerformanceObserver((list) => collected.push(...list.getEntries()))
        observer.observe({ type, buffered: true })
        // buffered なエントリは非同期に届く。1 フレーム分待ってから読む
        setTimeout(() => {
          observer.disconnect()
          resolve(collected)
        }, 50)
      })

    const [navigation] = performance.getEntriesByType('navigation') as PerformanceNavigationTiming[]
    if (!navigation) throw new Error('navigation エントリがありません')
    const fcp = performance.getEntriesByName('first-contentful-paint')[0]
    const lcpEntries = await observeBuffered('largest-contentful-paint')
    const lcp = lcpEntries.at(-1)
    const shifts = (await observeBuffered('layout-shift')) as (PerformanceEntry & {
      value: number
      hadRecentInput: boolean
    })[]
    const cls = shifts.filter((s) => !s.hadRecentInput).reduce((sum, s) => sum + s.value, 0)
    const resources = performance.getEntriesByType('resource') as PerformanceResourceTiming[]
    const kb = (bytes: number) => Math.round((bytes / 1024) * 10) / 10
    const sumBy = (suffix: string) =>
      resources
        .filter((r) => new URL(r.name).pathname.endsWith(suffix))
        .reduce((sum, r) => sum + r.transferSize, 0)

    return {
      ttfbMs: navigation.responseStart,
      domContentLoadedMs: navigation.domContentLoadedEventEnd,
      loadMs: navigation.loadEventEnd,
      fcpMs: fcp ? fcp.startTime : -1,
      lcpMs: lcp ? lcp.startTime : -1,
      cls,
      jsTransferKB: kb(sumBy('.js')),
      cssTransferKB: kb(sumBy('.css')),
      requestCount: resources.length,
    }
  })
}

export interface RuntimeMetrics {
  jsHeapUsedMB: number
  domNodes: number
}

/** CDP からヒープ・DOM ノード数を読む。GC を強制してから読むので、ゴミの量に左右されない */
export async function readRuntimeMetrics(page: Page): Promise<RuntimeMetrics> {
  const client = await page.context().newCDPSession(page)
  try {
    await client.send('Performance.enable')
    await client.send('HeapProfiler.collectGarbage')
    const { metrics } = await client.send('Performance.getMetrics')
    const value = (name: string) => metrics.find((m) => m.name === name)?.value ?? -1
    return {
      jsHeapUsedMB: Math.round((value('JSHeapUsedSize') / 1024 / 1024) * 10) / 10,
      domNodes: value('Nodes'),
    }
  } finally {
    await client.detach()
  }
}
