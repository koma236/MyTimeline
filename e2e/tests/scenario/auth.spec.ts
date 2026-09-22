// UC-01 新規登録 / UC-02 ログイン / UC-03 ログアウト（SCR-01・SCR-02）。
// 状態遷移: ゲスト → ログイン中 → ゲスト を画面から辿る。
// 認証周りだけは Cookie 注入（loginAs）ではなく実際の画面から入る。
import { expect, test } from '../../fixtures/test'
import { PASSWORD, nextUsername } from '../../fixtures/api-client'
import { header } from '../../fixtures/helpers'

function signupForm(page: Parameters<typeof header>[0]) {
  return {
    username: page.getByLabel('ユーザー名（@username）'),
    displayName: page.getByLabel('表示名'),
    email: page.getByLabel('メールアドレス', { exact: true }),
    password: page.getByLabel('パスワード'),
    submit: page.getByRole('button', { name: '登録する' }),
  }
}

function loginForm(page: Parameters<typeof header>[0]) {
  return {
    identifier: page.getByLabel('メールアドレス または ユーザー名'),
    password: page.getByLabel('パスワード'),
    submit: page.getByRole('button', { name: 'ログイン', exact: true }),
  }
}

test.describe('UC-01 新規登録', () => {
  test('状態遷移: 登録に成功するとログイン状態でタイムラインに遷移する', async ({ page }) => {
    const username = nextUsername()
    await page.goto('/signup')
    const form = signupForm(page)
    await form.username.fill(username)
    await form.displayName.fill('登録太郎')
    await form.email.fill(`${username}@example.com`)
    await form.password.fill(PASSWORD)
    await form.submit.click()

    await expect(page).toHaveURL('/')
    await expect(header(page).getByText('登録太郎')).toBeVisible()
    await expect(page.getByRole('tab', { name: 'すべて' })).toHaveAttribute('aria-selected', 'true')
  })

  test('同値分割: 使用済みの username は項目エラーになり登録されない', async ({ page, newUser }) => {
    const existing = await newUser()
    await page.goto('/signup')
    const form = signupForm(page)
    await form.username.fill(existing.username)
    await form.displayName.fill('重複太郎')
    await form.email.fill(`${nextUsername()}@example.com`)
    await form.password.fill(PASSWORD)
    await form.submit.click()

    await expect(form.username).toHaveAttribute('aria-invalid', 'true')
    await expect(page.locator('#su-username-error')).toBeVisible()
    await expect(page).toHaveURL(/\/signup$/)
  })

  test('境界値: パスワード 7 文字は項目エラー、形式不正のメールも項目エラー', async ({ page }) => {
    await page.goto('/signup')
    const form = signupForm(page)
    await form.username.fill(nextUsername())
    await form.displayName.fill('境界太郎')
    await form.email.fill('not-an-email')
    await form.password.fill('1234567')
    await form.submit.click()

    await expect(page.locator('#su-email-error')).toBeVisible()
    await expect(page.locator('#su-password-error')).toContainText('8文字以上')
    // 項目に紐づくエラーはフォーム全体の帯には出さない
    await expect(page.getByRole('alert')).toHaveCount(0)
  })

  test('境界値: パスワード 8 文字は登録できる', async ({ page }) => {
    const username = nextUsername()
    await page.goto('/signup')
    const form = signupForm(page)
    await form.username.fill(username)
    await form.displayName.fill('八文字太郎')
    await form.email.fill(`${username}@example.com`)
    await form.password.fill('12345678')
    await form.submit.click()
    await expect(page).toHaveURL('/')
  })
})

test.describe('UC-02 ログイン', () => {
  test('同値分割: username でログインできる', async ({ page, newUser }) => {
    const user = await newUser('ログイン')
    await page.goto('/login')
    const form = loginForm(page)
    await form.identifier.fill(user.username)
    await form.password.fill(user.password)
    await form.submit.click()
    await expect(page).toHaveURL('/')
    await expect(header(page).getByText(user.displayName)).toBeVisible()
  })

  test('同値分割: メールアドレスでもログインできる', async ({ page, newUser }) => {
    const user = await newUser('メール')
    await page.goto('/login')
    const form = loginForm(page)
    await form.identifier.fill(user.email)
    await form.password.fill(user.password)
    await form.submit.click()
    await expect(page).toHaveURL('/')
  })

  test('同値分割: パスワードが違うとフォーム全体のエラーになり、どの項目が誤りか明かさない', async ({
    page,
    newUser,
  }) => {
    const user = await newUser()
    await page.goto('/login')
    const form = loginForm(page)
    await form.identifier.fill(user.username)
    await form.password.fill('wrong-password')
    await form.submit.click()

    await expect(page.getByRole('alert')).toBeVisible()
    await expect(form.identifier).not.toHaveAttribute('aria-invalid', 'true')
    await expect(form.password).not.toHaveAttribute('aria-invalid', 'true')
    await expect(page).toHaveURL(/\/login$/)
  })
})

test.describe('UC-03 ログアウトとルーティングガード', () => {
  test('状態遷移: ログアウトするとログイン画面へ戻り、保護ルートに入れなくなる', async ({
    page,
    newUser,
    loginAs,
  }) => {
    const user = await newUser()
    await loginAs(page, user)
    await page.getByRole('button', { name: 'ログアウト' }).click()
    await expect(page).toHaveURL(/\/login$/)
    await expect(header(page)).toHaveCount(0)

    // 事後条件: 以降は認証が必要な画面を開けない（Cookie も失効している）
    await page.goto('/')
    await expect(page).toHaveURL(/\/login$/)
    await page.goto('/search')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('状態遷移: 未ログインで保護ルートを直接開くとログイン画面へ送られる', async ({ page }) => {
    await page.goto('/settings/profile')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('状態遷移: ログイン中に /login や /signup を開くとタイムラインへ戻される', async ({
    page,
    newUser,
    loginAs,
  }) => {
    await loginAs(page, await newUser())
    await page.goto('/login')
    await expect(page).toHaveURL('/')
    await page.goto('/signup')
    await expect(page).toHaveURL('/')
  })

  test('状態遷移: リロードしてもリフレッシュトークン（Cookie）でログイン状態が続く', async ({
    page,
    newUser,
    loginAs,
  }) => {
    const user = await newUser('継続')
    await loginAs(page, user)
    await page.reload()
    await expect(header(page).getByText(user.displayName)).toBeVisible()
  })

  test('同値分割: 未知のパスはタイムラインへ寄せられる', async ({ page, newUser, loginAs }) => {
    await loginAs(page, await newUser())
    await page.goto('/no-such-page')
    await expect(page).toHaveURL('/')
  })
})
