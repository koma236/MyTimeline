// ページ読み込みの計測。キャッシュの無い新しいコンテキストで PERF_RUNS 回開き、
// Navigation Timing / Paint Timing / LCP / CLS / 転送量を読む。
//
// 合否は Web Vitals の "good" の値（FCP 1.8 s / LCP 2.5 s / CLS 0.1 / TTFB 0.8 s）。
// 手元の Docker と vite preview での値なので絶対値の意味は薄く、前回との比較に使う（docs/14）。
import { expect, test } from '../../fixtures/test'
import { BASE_URL, PERF_RUNS } from '../../fixtures/env'
import { readPageLoad, recordMetric, type PageLoadMetrics } from '../../fixtures/perf'
import type { Browser, BrowserContext, TestInfo } from '@playwright/test'
import type { TestUser } from '../../fixtures/api-client'

async function freshContext(browser: Browser): Promise<BrowserContext> {
  return browser.newContext({
    baseURL: BASE_URL,
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
    viewport: { width: 1280, height: 720 },
  })
}

async function recordRuns(testInfo: TestInfo, screen: string, runs: PageLoadMetrics[]): Promise<void> {
  const pick = (key: keyof PageLoadMetrics) => runs.map((r) => r[key])
  const rows: Parameters<typeof recordMetric>[1][] = [
    { name: `${screen} TTFB`, unit: 'ms', values: pick('ttfbMs'), threshold: 800 },
    { name: `${screen} FCP`, unit: 'ms', values: pick('fcpMs'), threshold: 1800 },
    { name: `${screen} LCP`, unit: 'ms', values: pick('lcpMs'), threshold: 2500 },
    { name: `${screen} DOMContentLoaded`, unit: 'ms', values: pick('domContentLoadedMs') },
    { name: `${screen} load`, unit: 'ms', values: pick('loadMs') },
    { name: `${screen} CLS`, unit: 'score', values: pick('cls'), threshold: 0.1 },
    {
      name: `${screen} JS 転送量`,
      unit: 'KB',
      values: pick('jsTransferKB'),
      threshold: 250,
      note: 'vite preview は gzip 圧縮して配信する（本番の CloudFront と同等）。非圧縮は npm run build の出力を見る',
    },
    { name: `${screen} CSS 転送量`, unit: 'KB', values: pick('cssTransferKB') },
    { name: `${screen} リクエスト数`, unit: 'count', values: pick('requestCount') },
  ]
  for (const row of rows) await recordMetric(testInfo, row)
  const median = (values: number[]) => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)]!
  for (const row of rows) {
    if (row.threshold !== undefined) {
      expect(median(row.values), `${row.name} の中央値`).toBeLessThan(row.threshold)
    }
  }
}

test.describe('ページ読み込み', () => {
  test('SCR-01 ログイン画面（未ログイン・キャッシュ無し）', async ({ browser }, testInfo) => {
    const runs: PageLoadMetrics[] = []
    for (let i = 0; i < PERF_RUNS; i += 1) {
      const context = await freshContext(browser)
      const page = await context.newPage()
      await page.goto('/login', { waitUntil: 'load' })
      await expect(page.getByRole('heading', { name: 'MYTIMELINE' })).toBeVisible()
      // LCP は描画後しばらく更新されうるので、落ち着いてから読む
      await page.waitForTimeout(300)
      runs.push(await readPageLoad(page))
      await context.close()
    }
    await recordRuns(testInfo, 'ログイン画面', runs)
  })

  test('SCR-03 タイムライン（ログイン済み・投稿 60 件・キャッシュ無し）', async ({
    browser,
    api,
    newUser,
  }, testInfo) => {
    const me: TestUser = await newUser('perf')
    await api.createPosts(me, 60, '読み込み計測の投稿')

    const runs: PageLoadMetrics[] = []
    for (let i = 0; i < PERF_RUNS; i += 1) {
      const context = await freshContext(browser)
      // Cookie だけ入れて、初回描画（refresh → タイムライン取得 → 20 件描画）を丸ごと測る
      const login = await context.request.post('/api/auth/login', {
        data: { identifier: me.username, password: me.password },
      })
      expect(login.ok()).toBe(true)
      const page = await context.newPage()
      await page.goto('/', { waitUntil: 'load' })
      await expect(page.getByRole('article')).toHaveCount(20)
      await page.waitForTimeout(300)
      runs.push(await readPageLoad(page))
      await context.close()
    }
    await recordRuns(testInfo, 'タイムライン', runs)
  })
})
