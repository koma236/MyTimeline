// アクセシビリティ検査（axe-core）。SCR-01〜07 の 7 画面を代表的な状態で検査し、
// impact が serious / critical の違反が無いことを確かめる。
// 検査結果の全文はレポートに添付する（moderate 以下の指摘を拾う材料）。
import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '../../fixtures/test'
import { detailLink, postCard } from '../../fixtures/helpers'
import type { Page, TestInfo } from '@playwright/test'

// 初回の検査で見つかった既知の違反（docs/14_e2e_test.md「アクセシビリティ検査で見つかった課題」）。
// 直すまでは記録だけにして、これ以外の serious / critical が新たに増えたら失敗させる。
// 直したらこの一覧から外し、再発を防ぐ
const KNOWN_VIOLATIONS: Record<string, string> = {
  'color-contrast': 'text-muted / text-accent の文字色が白背景に対して 4.5:1 に届かない',
  'link-in-text-block': '本文中のリンク（「新規登録」「ログイン」）が色だけで区別されている',
  'link-name': 'アバターを包むリンク（投稿・コメント・検索結果）が aria-hidden の画像しか持たず、読み上げ名が無い',
}

async function expectNoSeriousViolations(page: Page, testInfo: TestInfo, screen: string): Promise<void> {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'best-practice']).analyze()
  await testInfo.attach(`axe:${screen}`, {
    body: JSON.stringify(results.violations, null, 2),
    contentType: 'application/json',
  })
  const describe = (v: (typeof results.violations)[number]) =>
    `${v.id} (${v.impact}): ${v.help} — ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`
  const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
  const known = serious.filter((v) => v.id in KNOWN_VIOLATIONS).map(describe)
  const unknown = serious.filter((v) => !(v.id in KNOWN_VIOLATIONS)).map(describe)
  if (known.length > 0) {
    await testInfo.attach(`axe-known:${screen}`, { body: known.join('\n'), contentType: 'text/plain' })
  }
  expect(unknown, `${screen} に既知以外の serious / critical の違反が無いこと`).toEqual([])
}

test.describe('アクセシビリティ（同値分割: 画面ごと）', () => {
  test('SCR-01 ログイン', async ({ page }, testInfo) => {
    await page.goto('/login')
    await expect(page.getByRole('heading', { name: 'MYTIMELINE' })).toBeVisible()
    await expectNoSeriousViolations(page, testInfo, 'SCR-01 ログイン')
  })

  test('SCR-02 新規登録', async ({ page }, testInfo) => {
    await page.goto('/signup')
    await expect(page.getByRole('heading', { name: 'アカウント作成' })).toBeVisible()
    await expectNoSeriousViolations(page, testInfo, 'SCR-02 新規登録')
  })

  test('SCR-03 タイムライン（投稿あり・メニュー展開）', async ({ page, api, newUser, loginAs }, testInfo) => {
    const me = await newUser()
    await api.createPost(me, 'a11y 検査用の投稿')
    await loginAs(page, me)
    await postCard(page, 'a11y 検査用の投稿').getByRole('button', { name: '投稿メニュー' }).click()
    await expectNoSeriousViolations(page, testInfo, 'SCR-03 タイムライン')
  })

  test('SCR-04 投稿詳細（コメントあり）', async ({ page, api, newUser, loginAs }, testInfo) => {
    const me = await newUser()
    const post = await api.createPost(me, 'a11y 詳細の投稿')
    await api.createComment(me, post.id, 'a11y 検査用のコメント')
    await loginAs(page, me)
    await detailLink(postCard(page, 'a11y 詳細の投稿')).click()
    await expect(page.getByRole('heading', { name: 'コメント 1' })).toBeVisible()
    await expectNoSeriousViolations(page, testInfo, 'SCR-04 投稿詳細')
  })

  test('SCR-05 プロフィール（他人・フォローボタンあり）', async ({ page, api, newUser, loginAs }, testInfo) => {
    const me = await newUser()
    const other = await newUser()
    await api.updateProfile(other, { displayName: other.displayName, bio: 'a11y 用の自己紹介' })
    await api.createPost(other, 'a11y プロフィールの投稿')
    await loginAs(page, me)
    await page.goto(`/users/${other.username}`)
    await expect(page.getByRole('heading', { name: other.displayName })).toBeVisible()
    await expectNoSeriousViolations(page, testInfo, 'SCR-05 プロフィール')
  })

  test('SCR-06 ユーザー検索（結果あり）', async ({ page, newUser, loginAs }, testInfo) => {
    const me = await newUser()
    const other = await newUser()
    await loginAs(page, me)
    await page.goto('/search')
    await page.getByPlaceholder('ユーザー名で検索...').fill(other.username)
    await expect(page.getByRole('article').filter({ hasText: `@${other.username}` })).toBeVisible()
    await expectNoSeriousViolations(page, testInfo, 'SCR-06 ユーザー検索')
  })

  test('SCR-07 プロフィール編集', async ({ page, newUser, loginAs }, testInfo) => {
    await loginAs(page, await newUser())
    await page.goto('/settings/profile')
    await expect(page.getByRole('heading', { name: 'プロフィールを編集' })).toBeVisible()
    await expectNoSeriousViolations(page, testInfo, 'SCR-07 プロフィール編集')
  })
})
