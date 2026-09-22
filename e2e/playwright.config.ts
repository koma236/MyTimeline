// シナリオ・耐性・アクセシビリティのテスト用設定（既定）。
// ブラウザ性能の計測は playwright.perf.config.ts（この設定を継承し、直列・1 worker に絞る）。
//
// 直接 `npx playwright test` を叩かず `bash e2e/run.sh run scenario` を使うこと。
// run.sh が backend の向き先（e2e 用 DB）を確認し、テーブルを空にしてから実行する。
import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { BASE_URL, FRONTEND_PORT } from './fixtures/env'

const here = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  testDir: './tests',
  // perf/ は別設定で回す。ここでは拾わない
  testIgnore: ['**/tests/perf/**'],
  globalSetup: './global-setup.ts',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  // 手元では 0 にして不安定なテストを隠さない。CI の共有ランナーだけ 1 回リトライを許す
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 2 : undefined,
  timeout: 30_000,
  expect: { timeout: 5_000 },
  reporter: [
    ['list'],
    // 失敗時のトレースとスクリーンショットを含む。`npm run report` で開く
    ['html', { open: 'never' }],
  ],
  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'ja-JP',
    timezoneId: 'Asia/Tokyo',
  },
  projects: [
    // docs/05_nonfunctional.md「ブラウザ対応: Google Chrome 最新版」なので chromium だけ
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    // 本番ビルド（vite preview）を使う。dev サーバーは非バンドル・非 minify で、
    // 性能計測の数値に意味が無いうえ、機能テストも同じサーバーに揃えて二重管理を避ける。
    // preview は server.proxy を継承するので /api → 8080 のプロキシは設定不要
    command: `npm run build && npx vite preview --port ${FRONTEND_PORT} --strictPort`,
    cwd: path.resolve(here, '../frontend'),
    url: BASE_URL,
    // 4173 が既に上がっていればそれを使う（古いビルドを掴む可能性は README に明記）。CI では毎回起動する
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
    stdout: 'ignore',
    stderr: 'pipe',
  },
})
