// 全テストで使う拡張 test。@playwright/test の代わりにここから import する。
//
//   api       … シードデータを API で投入する（worker ごとに 1 つの APIRequestContext を共有）
//   newUser   … 一意なユーザーを登録して返す
//   loginAs   … 既定の page をそのユーザーでログイン済みにしてホームを開く
//   openAs    … 別のブラウザコンテキスト（= 別の人）でログインした page を返す
//   browserErrors … 未捕捉の例外と console.error を自動で集め、1 件でもあれば失敗させる（auto）
import {
  expect,
  test as base,
  type APIRequestContext,
  type BrowserContext,
  type Page,
} from '@playwright/test'
import { ApiClient, type TestUser } from './api-client'
import { API_URL, BASE_URL } from './env'

// Chrome が 4xx / 5xx の応答ごとに出す定型ログ。意図的に失敗させるテスト（誤パスワード・
// 耐性テスト）で必ず出るので除外する。アプリ自身の console.error は除外しない
const IGNORED_CONSOLE = [/^Failed to load resource/]

function watchErrors(page: Page, sink: string[]): void {
  page.on('pageerror', (error) => sink.push(`pageerror: ${error.message}`))
  page.on('console', (message) => {
    if (message.type() !== 'error') return
    if (IGNORED_CONSOLE.some((pattern) => pattern.test(message.text()))) return
    sink.push(`console.error: ${message.text()}`)
  })
}

/**
 * Cookie 経由でログイン済みにする。
 *
 * アクセストークンはメモリ、リフレッシュトークンは httpOnly Cookie なので storageState は使えない。
 * context.request はブラウザと Cookie jar を共有するため、ここで login を呼ぶと Set-Cookie が
 * そのままブラウザに入り、次の page.goto で AuthProvider が /auth/refresh を叩いてログイン状態になる。
 * 画面からのログインは UC-02 のテストでだけ行う。
 */
export async function loginViaCookie(page: Page, user: TestUser): Promise<void> {
  const response = await page.context().request.post('/api/auth/login', {
    data: { identifier: user.username, password: user.password },
  })
  if (!response.ok()) {
    throw new Error(`ログインに失敗しました（${user.username}）: ${response.status()}`)
  }
  await page.goto('/')
  await expect(page.getByRole('button', { name: 'ログアウト' })).toBeVisible()
}

interface Fixtures {
  api: ApiClient
  newUser: (label?: string) => Promise<TestUser>
  loginAs: (page: Page, user: TestUser) => Promise<void>
  openAs: (user: TestUser) => Promise<Page>
  browserErrors: string[]
}

interface WorkerFixtures {
  apiContext: APIRequestContext
}

export const test = base.extend<Fixtures, WorkerFixtures>({
  apiContext: [
    async ({ playwright }, use) => {
      // ブラウザの Cookie jar と分けるため、専用のコンテキストで backend を直接叩く
      const context = await playwright.request.newContext({ baseURL: API_URL })
      await use(context)
      await context.dispose()
    },
    { scope: 'worker' },
  ],

  api: async ({ apiContext }, use) => {
    await use(new ApiClient(apiContext))
  },

  newUser: async ({ api }, use) => {
    await use((label) => api.signup(label))
  },

  browserErrors: [
    async ({ page }, use) => {
      const errors: string[] = []
      watchErrors(page, errors)
      await use(errors)
      expect(errors, 'ブラウザで未捕捉の例外や console.error が出ていないこと').toEqual([])
    },
    { auto: true },
  ],

  loginAs: async ({}, use) => {
    await use(loginViaCookie)
  },

  openAs: async ({ browser, browserErrors }, use) => {
    const contexts: BrowserContext[] = []
    await use(async (user) => {
      // browser.newContext は設定ファイルの use を引き継がないので、必要なものだけ渡す
      const context = await browser.newContext({
        baseURL: BASE_URL,
        locale: 'ja-JP',
        timezoneId: 'Asia/Tokyo',
        viewport: { width: 1280, height: 720 },
      })
      contexts.push(context)
      const page = await context.newPage()
      watchErrors(page, browserErrors)
      await loginViaCookie(page, user)
      return page
    })
    for (const context of contexts) await context.close()
  },
})

export { expect }
export type { TestUser }
