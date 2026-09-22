// UC-09 投稿にいいねする / 取り消す（F05）。
import { expect, test } from '../../fixtures/test'
import { likeButton, postCard } from '../../fixtures/helpers'

test.describe('UC-09 いいね', () => {
  test('状態遷移: いいね → ♥ と件数 1 → 取り消し → 件数 0', async ({ page, api, newUser, loginAs }) => {
    const author = await newUser('作者')
    const me = await newUser('自分')
    await api.createPost(author, 'いいねされる投稿')
    await loginAs(page, me)

    const button = likeButton(postCard(page, 'いいねされる投稿'))
    await expect(button).toHaveAttribute('aria-pressed', 'false')
    await expect(button).toContainText('0')

    await button.click()
    await expect(button).toHaveAttribute('aria-pressed', 'true')
    await expect(button).toContainText('1')

    await button.click()
    await expect(button).toHaveAttribute('aria-pressed', 'false')
    await expect(button).toContainText('0')
  })

  test('状態遷移: いいねはリロードしても保持される（サーバーに保存されている）', async ({
    page,
    api,
    newUser,
    loginAs,
  }) => {
    const me = await newUser()
    await api.createPost(me, '保持される いいね')
    await loginAs(page, me)
    await likeButton(postCard(page, '保持される いいね')).click()
    await expect(likeButton(postCard(page, '保持される いいね'))).toHaveAttribute('aria-pressed', 'true')

    await page.reload()
    const button = likeButton(postCard(page, '保持される いいね'))
    await expect(button).toHaveAttribute('aria-pressed', 'true')
    await expect(button).toContainText('1')
  })

  test('同値分割: 別のユーザーのいいねは件数にだけ反映され、自分の状態にはならない', async ({
    page,
    api,
    newUser,
    loginAs,
    openAs,
  }) => {
    const me = await newUser('自分')
    const other = await newUser('別人')
    await api.createPost(me, 'ふたりで いいね')
    await loginAs(page, me)

    const otherPage = await openAs(other)
    await likeButton(postCard(otherPage, 'ふたりで いいね')).click()
    await expect(likeButton(postCard(otherPage, 'ふたりで いいね'))).toContainText('1')

    await page.reload()
    const mine = likeButton(postCard(page, 'ふたりで いいね'))
    await expect(mine).toContainText('1')
    await expect(mine).toHaveAttribute('aria-pressed', 'false')

    // 自分も押すと 2 になり、相手が取り消しても自分の分は残る
    await mine.click()
    await expect(mine).toContainText('2')
    await likeButton(postCard(otherPage, 'ふたりで いいね')).click()
    await page.reload()
    await expect(likeButton(postCard(page, 'ふたりで いいね'))).toContainText('1')
    await expect(likeButton(postCard(page, 'ふたりで いいね'))).toHaveAttribute('aria-pressed', 'true')
  })

  test('同値分割: 投稿詳細画面でもいいねできる', async ({ page, api, newUser, loginAs }) => {
    const me = await newUser()
    const post = await api.createPost(me, '詳細で いいね')
    await loginAs(page, me)
    await page.goto(`/posts/${post.id}`)
    const button = likeButton(postCard(page, '詳細で いいね'))
    await button.click()
    await expect(button).toHaveAttribute('aria-pressed', 'true')
    await expect(button).toContainText('1')
  })
})
