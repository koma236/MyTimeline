// UC-07 投稿にコメントする / UC-08 自分のコメントを削除する（F04・SCR-04）。
import { expect, test } from '../../fixtures/test'
import { answerConfirm, commentCard, detailLink, editingCard, postCard, repeat } from '../../fixtures/helpers'

function commentComposer(page: Parameters<typeof postCard>[0]) {
  const form = page.locator('form').filter({ has: page.getByLabel('コメントの本文') })
  return {
    body: page.getByLabel('コメントの本文'),
    submit: page.getByRole('button', { name: '返信', exact: true }),
    remaining: form.locator('span').filter({ hasText: /^-?\d+$/ }),
  }
}

test.describe('UC-07 投稿にコメントする', () => {
  test('状態遷移: タイムライン → 詳細 → コメント投稿 → 件数が 1 増え、戻っても反映されている', async ({
    page,
    api,
    newUser,
    loginAs,
  }) => {
    const author = await newUser('作者')
    const me = await newUser('自分')
    const post = await api.createPost(author, 'コメントされる投稿')
    await loginAs(page, me)

    await detailLink(postCard(page, 'コメントされる投稿')).click()
    await expect(page).toHaveURL(`/posts/${post.id}`)
    await expect(page.getByText('まだコメントはありません')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'コメント 0' })).toBeVisible()

    const { body, submit } = commentComposer(page)
    await body.fill('最初のコメント')
    await submit.click()
    await expect(commentCard(page, '最初のコメント')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'コメント 1' })).toBeVisible()
    await expect(body).toHaveValue('')

    await page.getByRole('link', { name: '← タイムライン' }).click()
    await expect(page).toHaveURL('/')
    await expect(
      postCard(page, 'コメントされる投稿').getByRole('link', { name: /コメントを見る/ }),
    ).toContainText('1')
  })

  test('境界値: 501 文字は返信できずカウンタが -1、500 文字は返信できる', async ({
    page,
    api,
    newUser,
    loginAs,
  }) => {
    const me = await newUser()
    const post = await api.createPost(me, '長いコメントの投稿')
    await loginAs(page, me)
    await page.goto(`/posts/${post.id}`)

    const { body, submit, remaining } = commentComposer(page)
    await body.fill(repeat('あ', 501))
    await expect(remaining).toHaveText('-1')
    await expect(submit).toBeDisabled()

    const exact = `${repeat('い', 499)}終`
    await body.fill(exact)
    await expect(remaining).toHaveText('0')
    await submit.click()
    await expect(commentCard(page, exact)).toBeVisible()
  })

  test('同値分割: 空白だけのコメントは送れない', async ({ page, api, newUser, loginAs }) => {
    const me = await newUser()
    const post = await api.createPost(me, '空コメントの投稿')
    await loginAs(page, me)
    await page.goto(`/posts/${post.id}`)
    const { body, submit } = commentComposer(page)
    await expect(submit).toBeDisabled()
    await body.fill('  ')
    await expect(submit).toBeDisabled()
  })
})

test.describe('UC-08 自分のコメントを編集・削除する', () => {
  test('状態遷移: 編集すると本文が置き換わり「（編集済み）」が付く', async ({
    page,
    api,
    newUser,
    loginAs,
  }) => {
    const me = await newUser()
    const post = await api.createPost(me, '編集コメントの投稿')
    await api.createComment(me, post.id, '編集前のコメント')
    await loginAs(page, me)
    await page.goto(`/posts/${post.id}`)

    const card = commentCard(page, '編集前のコメント')
    await card.getByRole('button', { name: 'コメントメニュー' }).click()
    await card.getByRole('button', { name: '編集' }).click()
    const editing = editingCard(page, 'コメントの本文を編集')
    await editing.getByLabel('コメントの本文を編集').fill('編集後のコメント')
    await editing.getByRole('button', { name: '保存', exact: true }).click()

    const edited = commentCard(page, '編集後のコメント')
    await expect(edited).toBeVisible()
    await expect(edited).toContainText('（編集済み）')
  })

  test('デシジョンテーブル: 削除の確認を却下すると残り、承諾すると消えて件数が 1 減る', async ({
    page,
    api,
    newUser,
    loginAs,
  }) => {
    const me = await newUser()
    const post = await api.createPost(me, '削除コメントの投稿')
    await api.createComment(me, post.id, '消えるコメント')
    await api.createComment(me, post.id, '残るコメント')
    await loginAs(page, me)
    await page.goto(`/posts/${post.id}`)
    await expect(page.getByRole('heading', { name: 'コメント 2' })).toBeVisible()

    const card = commentCard(page, '消えるコメント')
    answerConfirm(page, false)
    await card.getByRole('button', { name: 'コメントメニュー' }).click()
    await card.getByRole('button', { name: '削除' }).click()
    await expect(card).toBeVisible()
    await expect(page.getByRole('heading', { name: 'コメント 2' })).toBeVisible()

    answerConfirm(page, true)
    await card.getByRole('button', { name: 'コメントメニュー' }).click()
    await card.getByRole('button', { name: '削除' }).click()
    await expect(commentCard(page, '消えるコメント')).toHaveCount(0)
    await expect(commentCard(page, '残るコメント')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'コメント 1' })).toBeVisible()
  })

  test('デシジョンテーブル: 他人のコメントにはメニューが出ない', async ({
    page,
    api,
    newUser,
    loginAs,
  }) => {
    const other = await newUser('他人')
    const me = await newUser('自分')
    const post = await api.createPost(other, '他人コメントの投稿')
    await api.createComment(other, post.id, '他人のコメント')
    await api.createComment(me, post.id, '自分のコメント')
    await loginAs(page, me)
    await page.goto(`/posts/${post.id}`)

    await expect(commentCard(page, '自分のコメント').getByRole('button', { name: 'コメントメニュー' })).toBeVisible()
    await expect(commentCard(page, '他人のコメント')).toBeVisible()
    await expect(commentCard(page, '他人のコメント').getByRole('button', { name: 'コメントメニュー' })).toHaveCount(0)
  })
})
