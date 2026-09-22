// F07 プロフィール編集（SCR-07）。表示名・自己紹介・プロフィール画像。
import { expect, test } from '../../fixtures/test'
import { tinyPng } from '../../fixtures/images'
import { answerConfirm, header, repeat } from '../../fixtures/helpers'
import type { Page } from '@playwright/test'

function editForm(page: Page) {
  const form = page.locator('form').filter({ has: page.getByLabel('自己紹介') })
  return {
    displayName: page.getByLabel('表示名'),
    bio: page.getByLabel('自己紹介'),
    save: page.getByRole('button', { name: '保存する' }),
    remaining: form.locator('span').filter({ hasText: /^-?\d+$/ }),
  }
}

test.describe('F07 プロフィール編集', () => {
  test('状態遷移: 表示名と自己紹介を保存するとプロフィールとヘッダーに反映される', async ({
    page,
    newUser,
    loginAs,
  }) => {
    const me = await newUser('編集前')
    await loginAs(page, me)
    await page.goto(`/users/${me.username}`)
    await page.getByRole('link', { name: 'プロフィールを編集' }).click()
    await expect(page).toHaveURL(/\/settings\/profile$/)

    const form = editForm(page)
    await expect(form.displayName).toHaveValue(me.displayName)
    await form.displayName.fill('編集後の名前')
    await form.bio.fill('自己紹介です。\n2 行目')
    await form.save.click()

    await expect(page).toHaveURL(`/users/${me.username}`)
    await expect(page.getByRole('heading', { name: '編集後の名前' })).toBeVisible()
    await expect(page.getByText('自己紹介です。')).toBeVisible()
    await expect(header(page).getByText('編集後の名前')).toBeVisible()

    // 事後条件: リロードしても保存されている
    await page.reload()
    await expect(page.getByRole('heading', { name: '編集後の名前' })).toBeVisible()
  })

  test('境界値: 自己紹介 301 文字は保存できずカウンタが -1、300 文字は保存できる', async ({
    page,
    newUser,
    loginAs,
  }) => {
    await loginAs(page, await newUser())
    await page.goto('/settings/profile')
    const form = editForm(page)

    await form.bio.fill(repeat('あ', 301))
    await expect(form.remaining).toHaveText('-1')
    await expect(form.save).toBeDisabled()

    await form.bio.fill(repeat('い', 300))
    await expect(form.remaining).toHaveText('0')
    await expect(form.save).toBeEnabled()
    await form.save.click()
    await expect(page).toHaveURL(/\/users\//)
    await expect(page.getByText(repeat('い', 300))).toBeVisible()
  })

  test('同値分割: 表示名が空だと保存できない', async ({ page, newUser, loginAs }) => {
    await loginAs(page, await newUser())
    await page.goto('/settings/profile')
    const form = editForm(page)
    await form.displayName.fill('')
    await expect(form.save).toBeDisabled()
    await form.displayName.fill('   ')
    await expect(form.save).toBeDisabled()
  })

  test('状態遷移: プロフィール画像をアップロードすると表示され、削除すると初期表示に戻る', async ({
    page,
    newUser,
    loginAs,
  }) => {
    const me = await newUser('画像')
    await loginAs(page, me)
    await page.goto('/settings/profile')
    await expect(page.getByRole('button', { name: '画像を削除' })).toHaveCount(0)

    await page.getByLabel('プロフィール画像を選択').setInputFiles(tinyPng('avatar.png'))
    await expect(page.getByRole('button', { name: '画像を削除' })).toBeVisible()
    // 署名付き URL から実際に読めていること（ヘッダーのアバターにも同じ画像が出る）。
    // Avatar の img は alt="" + aria-hidden（装飾扱い）なのでロールでは引けず、要素で取る
    const avatar = header(page).locator('img')
    await expect(avatar).toBeVisible()
    await expect.poll(() => avatar.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0)

    answerConfirm(page, true)
    await page.getByRole('button', { name: '画像を削除' }).click()
    await expect(page.getByRole('button', { name: '画像を削除' })).toHaveCount(0)
    await expect(header(page).locator('img')).toHaveCount(0)
  })
})
