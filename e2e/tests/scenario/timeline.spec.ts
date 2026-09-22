// UC-04 タイムラインを閲覧する（F02・SCR-03）。タブの切り替えと無限スクロール。
//
// 「すべて」タブは並列で走る他のテストの投稿も混ざるので、件数の断定は
// 自分とフォロー先だけが出る「フォロー中」タブで行う。
import { expect, test } from '../../fixtures/test'
import { postCard, timelineTab } from '../../fixtures/helpers'
import type { Page } from '@playwright/test'

/** 末尾までスクロールして expectedTotal 件になるまで読み込む */
async function scrollUntilLoaded(page: Page, expectedTotal: number): Promise<void> {
  const articles = page.getByRole('article')
  let count = await articles.count()
  while (count < expectedTotal) {
    await articles.last().scrollIntoViewIfNeeded()
    await expect.poll(() => articles.count(), { timeout: 5_000 }).toBeGreaterThan(count)
    count = await articles.count()
  }
}

test.describe('UC-04 タイムラインを閲覧する', () => {
  test('デシジョンテーブル: 自分 / フォロー先 / 無関係 の投稿 × すべて / フォロー中 タブ', async ({
    page,
    api,
    newUser,
    loginAs,
  }) => {
    const me = await newUser('自分')
    const followed = await newUser('フォロー先')
    const stranger = await newUser('無関係')
    await api.follow(me, followed.id)
    await api.createPost(me, 'TL 自分の投稿')
    await api.createPost(followed, 'TL フォロー先の投稿')
    await api.createPost(stranger, 'TL 無関係の投稿')

    await loginAs(page, me)
    // 既定は「すべて」
    await expect(timelineTab(page, 'すべて')).toHaveAttribute('aria-selected', 'true')
    await expect(postCard(page, 'TL 自分の投稿')).toBeVisible()
    await expect(postCard(page, 'TL フォロー先の投稿')).toBeVisible()
    await expect(postCard(page, 'TL 無関係の投稿')).toBeVisible()

    await timelineTab(page, 'フォロー中').click()
    await expect(timelineTab(page, 'フォロー中')).toHaveAttribute('aria-selected', 'true')
    await expect(postCard(page, 'TL 自分の投稿')).toBeVisible()
    await expect(postCard(page, 'TL フォロー先の投稿')).toBeVisible()
    await expect(postCard(page, 'TL 無関係の投稿')).toHaveCount(0)
    // フォロー中の 2 件は新しい順
    await expect(page.getByRole('article').first()).toContainText('TL フォロー先の投稿')

    await timelineTab(page, 'すべて').click()
    await expect(postCard(page, 'TL 無関係の投稿')).toBeVisible()
  })

  test('同値分割: 投稿にはいいね数とコメント数が表示される', async ({ page, api, newUser, loginAs }) => {
    const me = await newUser()
    const other = await newUser()
    const post = await api.createPost(other, '数字つきの投稿')
    await api.likePost(me, post.id)
    await api.createComment(me, post.id, 'コメント 1 件目')
    await api.createComment(other, post.id, 'コメント 2 件目')

    await loginAs(page, me)
    const card = postCard(page, '数字つきの投稿')
    await expect(card.getByRole('button', { name: /いいね/ })).toContainText('1')
    await expect(card.getByRole('link', { name: /コメントを見る/ })).toContainText('2')
  })

  test('状態遷移: フォロー中が空ならユーザー検索への導線が出る（UC-04 代替フロー）', async ({
    page,
    newUser,
    loginAs,
  }) => {
    await loginAs(page, await newUser())
    await timelineTab(page, 'フォロー中').click()
    await expect(page.getByText('まだ投稿がありません')).toBeVisible()
    await page.getByRole('link', { name: 'ユーザーを検索してフォローする' }).click()
    await expect(page).toHaveURL(/\/search$/)
  })

  test('境界値: 45 件は 20 / 20 / 5 と無限スクロールで読み込まれ、重複も欠落もない', async ({
    page,
    api,
    newUser,
    loginAs,
  }) => {
    const me = await newUser()
    await api.createPosts(me, 45, 'スクロール投稿')
    await loginAs(page, me)
    await timelineTab(page, 'フォロー中').click()

    const articles = page.getByRole('article')
    await expect(articles).toHaveCount(20)
    await expect(articles.first()).toContainText('スクロール投稿 45')

    await scrollUntilLoaded(page, 45)
    await expect(articles).toHaveCount(45)
    await expect(articles.last()).toContainText('スクロール投稿 1')

    // 末尾に達したら番兵が消え、それ以上は取りに行かない
    await articles.last().scrollIntoViewIfNeeded()
    await page.waitForTimeout(500)
    await expect(articles).toHaveCount(45)

    const bodies = await articles.allInnerTexts()
    const numbers = bodies.map((text) => /スクロール投稿 (\d+)/.exec(text)?.[1]).filter(Boolean)
    expect(new Set(numbers).size).toBe(45)
  })
})
