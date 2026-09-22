// シナリオでは通らない「API が失敗したとき」の挙動。page.route で応答を差し替えて再現する。
//
// 確かめるのは frontend/src/api/client.ts の集約リフレッシュと、各画面の再読み込み導線が
// 実ブラウザで期待どおり動くこと（単体テストはモックした axios で見ているだけ）。
import { expect, test } from '../../fixtures/test'
import { postCard, timelineTab } from '../../fixtures/helpers'
import type { Page } from '@playwright/test'

/** 指定パスへの最初の times 回だけ status で応答し、以降は素通しにする */
async function failFirst(page: Page, pattern: RegExp, status: number, times = 1): Promise<void> {
  let remaining = times
  await page.route(pattern, async (route) => {
    if (remaining > 0) {
      remaining -= 1
      await route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify({ message: `E2E が差し替えた ${status} 応答` }),
      })
      return
    }
    await route.continue()
  })
}

test.describe('API 失敗時の耐性', () => {
  test('状態遷移: タイムラインの取得が 500 → エラーと再読み込みボタン → 再読み込みで復帰', async ({
    page,
    api,
    newUser,
    loginAs,
  }) => {
    const me = await newUser()
    await api.createPost(me, '復帰後に見える投稿')
    await failFirst(page, /\/api\/timeline\/all/, 500)
    await loginAs(page, me)

    await expect(page.getByRole('alert')).toContainText('500 応答')
    await expect(page.getByRole('article')).toHaveCount(0)

    await page.getByRole('button', { name: '再読み込み' }).click()
    await expect(page.getByRole('alert')).toHaveCount(0)
    await expect(postCard(page, '復帰後に見える投稿')).toBeVisible()
  })

  test('状態遷移: API が 401 を返しても自動でリフレッシュして再送し、画面は壊れない', async ({
    page,
    api,
    newUser,
    loginAs,
  }) => {
    const me = await newUser()
    await api.createPost(me, '再送後に見える投稿')
    await loginAs(page, me)

    // アクセストークンが切れた状況を再現する。refresh は本物を通す
    await failFirst(page, /\/api\/timeline\/following/, 401)
    await timelineTab(page, 'フォロー中').click()

    await expect(postCard(page, '再送後に見える投稿')).toBeVisible()
    await expect(page.getByRole('alert')).toHaveCount(0)
    await expect(page).toHaveURL('/')
  })

  test('状態遷移: リフレッシュも失敗する（セッション切れ）とログイン画面へ送られる', async ({
    page,
    newUser,
    loginAs,
  }) => {
    await loginAs(page, await newUser())
    await failFirst(page, /\/api\/auth\/refresh/, 401)
    await failFirst(page, /\/api\/timeline\/following/, 401)

    await timelineTab(page, 'フォロー中').click()
    await expect(page).toHaveURL(/\/login$/)
  })

  test('同値分割: サーバーに到達できないときは接続エラーの文言を出す', async ({ page }) => {
    await page.route(/\/api\/auth\/login/, (route) => route.abort('connectionrefused'))
    await page.goto('/login')
    await page.getByLabel('メールアドレス または ユーザー名').fill('someone')
    await page.getByLabel('パスワード').fill('whatever123')
    await page.getByRole('button', { name: 'ログイン', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('サーバーに接続できませんでした')
  })

  test('状態遷移: 投稿の送信が失敗しても入力内容は残り、理由が表示される', async ({
    page,
    newUser,
    loginAs,
  }) => {
    await loginAs(page, await newUser())
    await failFirst(page, /\/api\/posts$/, 500)
    await page.getByLabel('投稿の本文').fill('失敗しても残る本文')
    await page.getByRole('button', { name: '投稿', exact: true }).click()

    await expect(page.getByRole('alert')).toContainText('500 応答')
    await expect(page.getByLabel('投稿の本文')).toHaveValue('失敗しても残る本文')

    // 再送すると通る
    await page.getByRole('button', { name: '投稿', exact: true }).click()
    await expect(postCard(page, '失敗しても残る本文')).toBeVisible()
    await expect(page.getByRole('alert')).toHaveCount(0)
  })
})
