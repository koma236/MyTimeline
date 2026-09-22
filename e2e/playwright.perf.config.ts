// ブラウザ性能の計測用設定。playwright.config.ts を継承し、計測がぶれないよう
// 直列・1 worker・リトライ無しで tests/perf/ だけを実行する。
//   bash e2e/run.sh run perf
import { defineConfig } from '@playwright/test'
import base from './playwright.config'

export default defineConfig(base, {
  testIgnore: [],
  testMatch: ['**/tests/perf/**/*.spec.ts'],
  fullyParallel: false,
  workers: 1,
  retries: 0,
  // 1 テストで操作を E2E_PERF_RUNS 回繰り返すので長めに取る
  timeout: 180_000,
  reporter: [['list'], ['html', { open: 'never' }], ['./reporters/perf-reporter.ts']],
})
