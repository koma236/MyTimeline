// UC-10 フォロー / 解除、UC-11 ユーザー検索、UC-12 プロフィール閲覧（F06・F07・SCR-05・SCR-06）。
import { expect, test } from '../../fixtures/test'
import { header, postCard, timelineTab } from '../../fixtures/helpers'
import type { Page } from '@playwright/test'

function searchBox(page: Page) {
  return page.getByPlaceholder('ユーザー名で検索...')
}

function userRow(page: Page, username: string) {
  return page.getByRole('article').filter({ hasText: `@${username}` })
}

function followButton(scope: ReturnType<typeof userRow> | Page) {
  return scope.getByRole('button', { name: /^フォロー(中)?$/ })
}

function followerCount(page: Page, count: number) {
  return page.locator('span').filter({ hasText: new RegExp(`^${count}フォロワー$`) })
}

test.describe('UC-11 ユーザーを検索する', () => {
  test('同値分割: username の一部でヒットし、自分の行にはフォローボタンが無い', async ({
    page,
    newUser,
    loginAs,
  }) => {
    const me = await newUser('自分')
    const target = await newUser('さがす')
    await loginAs(page, me)
    await header(page).getByRole('link', { name: /検索/ }).click()
    await expect(page).toHaveURL(/\/search$/)

    // 一意な username の末尾だけで検索する（部分一致）
    await searchBox(page).fill(target.username.slice(4))
    await expect(userRow(page, target.username)).toBeVisible()
    await expect(followButton(userRow(page, target.username))).toHaveText('フォロー')

    await searchBox(page).fill(me.username)
    await expect(userRow(page, me.username)).toBeVisible()
    await expect(followButton(userRow(page, me.username))).toHaveCount(0)
  })

  test('同値分割: 該当なしのときは文言を出す', async ({ page, newUser, loginAs }) => {
    await loginAs(page, await newUser())
    await page.goto('/search')
    await searchBox(page).fill('zzz_no_such_user_zzz')
    await expect(page.getByText('該当するユーザーがいません。')).toBeVisible()
  })
})

test.describe('UC-10 フォロー / 解除', () => {
  test('状態遷移: 検索結果からフォロー → プロフィールのフォロワー数が双方で 1 → 解除で 0', async ({
    page,
    newUser,
    loginAs,
    openAs,
  }) => {
    const me = await newUser('自分')
    const target = await newUser('相手')
    await loginAs(page, me)
    await page.goto('/search')
    await searchBox(page).fill(target.username)

    const row = userRow(page, target.username)
    await followButton(row).click()
    await expect(followButton(row)).toHaveText('フォロー中')
    await expect(followButton(row)).toHaveAttribute('aria-pressed', 'true')

    // 検索結果からプロフィールへ（UC-11 基本フロー 3）
    await row.getByRole('link', { name: new RegExp(target.displayName) }).click()
    await expect(page).toHaveURL(`/users/${target.username}`)
    await expect(page.getByRole('heading', { name: target.displayName })).toBeVisible()
    await expect(followerCount(page, 1)).toBeVisible()
    await expect(followButton(page)).toHaveText('フォロー中')

    // 相手側から見ても同じ数字
    const targetPage = await openAs(target)
    await targetPage.goto(`/users/${target.username}`)
    await expect(followerCount(targetPage, 1)).toBeVisible()

    await followButton(page).click()
    await expect(followButton(page)).toHaveText('フォロー')
    await expect(followerCount(page, 0)).toBeVisible()
    await targetPage.reload()
    await expect(followerCount(targetPage, 0)).toBeVisible()
  })

  test('状態遷移: フォローするとフォロー中タイムラインに相手の投稿が現れる（UC-04 との連携）', async ({
    page,
    api,
    newUser,
    loginAs,
  }) => {
    const me = await newUser('自分')
    const target = await newUser('相手')
    await api.createPost(target, 'フォロー前からある投稿')
    await loginAs(page, me)

    await timelineTab(page, 'フォロー中').click()
    await expect(page.getByText('まだ投稿がありません')).toBeVisible()

    await page.goto(`/users/${target.username}`)
    await followButton(page).click()
    await expect(followButton(page)).toHaveText('フォロー中')

    await page.goto('/')
    await timelineTab(page, 'フォロー中').click()
    await expect(postCard(page, 'フォロー前からある投稿')).toBeVisible()
  })
})

test.describe('UC-12 プロフィールを閲覧する', () => {
  test('デシジョンテーブル: 自分のプロフィールには編集リンク、他人にはフォローボタン', async ({
    page,
    api,
    newUser,
    loginAs,
  }) => {
    const me = await newUser('自分')
    const other = await newUser('他人')
    await api.createPost(other, '他人のプロフィール投稿')
    await api.createPost(me, '自分のプロフィール投稿')
    await loginAs(page, me)

    // 投稿の作者名からプロフィールへ（UC-12 基本フロー 1）
    await postCard(page, '他人のプロフィール投稿').getByRole('link', { name: new RegExp(other.displayName) }).click()
    await expect(page).toHaveURL(`/users/${other.username}`)
    await expect(page.getByRole('heading', { name: other.displayName })).toBeVisible()
    await expect(page.locator('p', { hasText: `@${other.username}` })).toBeVisible()
    await expect(page.getByText(/\d{4}年\d{1,2}月から利用/)).toBeVisible()
    await expect(followButton(page)).toBeVisible()
    await expect(page.getByRole('link', { name: 'プロフィールを編集' })).toHaveCount(0)
    await expect(postCard(page, '他人のプロフィール投稿')).toBeVisible()
    await expect(postCard(page, '自分のプロフィール投稿')).toHaveCount(0)

    // ヘッダーから自分のプロフィールへ
    await header(page).getByRole('link', { name: new RegExp(me.displayName) }).click()
    await expect(page).toHaveURL(`/users/${me.username}`)
    await expect(page.getByRole('link', { name: 'プロフィールを編集' })).toBeVisible()
    await expect(followButton(page)).toHaveCount(0)
    await expect(postCard(page, '自分のプロフィール投稿')).toBeVisible()
  })

  test('同値分割: 存在しないユーザーはエラーとタイムラインへの導線を出す', async ({
    page,
    newUser,
    loginAs,
  }) => {
    await loginAs(page, await newUser())
    await page.goto('/users/no_such_user_zzz')
    await expect(page.getByRole('alert')).toBeVisible()
    await page.getByRole('link', { name: 'タイムラインへ戻る' }).click()
    await expect(page).toHaveURL('/')
  })
})
