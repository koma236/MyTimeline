// UC-05 投稿を作成する / UC-06 自分の投稿を削除する / 投稿の編集（F03・SCR-03）。
import { expect, test } from '../../fixtures/test'
import { fakePng, tinyPng } from '../../fixtures/images'
import { answerConfirm, composer, editingCard, postCard, postViaUi, repeat } from '../../fixtures/helpers'

test.describe('UC-05 投稿を作成する', () => {
  test('状態遷移: 投稿するとタイムラインの先頭に現れ、入力欄が空に戻る', async ({
    page,
    newUser,
    loginAs,
  }) => {
    await loginAs(page, await newUser('投稿'))
    const body = `はじめての投稿 ${Date.now()}`
    await postViaUi(page, body)

    await expect(page.getByRole('article').first()).toContainText(body)
    const { body: textarea, remaining } = composer(page)
    await expect(textarea).toHaveValue('')
    await expect(remaining).toHaveText('280')
  })

  test('境界値: 281 文字は投稿できずカウンタが -1、280 文字は投稿できる', async ({
    page,
    newUser,
    loginAs,
  }) => {
    await loginAs(page, await newUser())
    const { body, submit, remaining } = composer(page)

    await body.fill(repeat('あ', 281))
    await expect(remaining).toHaveText('-1')
    await expect(submit).toBeDisabled()

    const exact = `${repeat('い', 279)}終`
    await body.fill(exact)
    await expect(remaining).toHaveText('0')
    await expect(submit).toBeEnabled()
    await submit.click()
    await expect(postCard(page, exact).first()).toBeVisible()
  })

  test('同値分割: 本文が空、または空白だけでは投稿ボタンが押せない', async ({
    page,
    newUser,
    loginAs,
  }) => {
    await loginAs(page, await newUser())
    const { body, submit } = composer(page)
    await expect(submit).toBeDisabled()
    await body.fill('   ')
    await expect(submit).toBeDisabled()
  })

  test('状態遷移: PNG を添付するとプレビューが出て、投稿後に画像が表示される', async ({
    page,
    newUser,
    loginAs,
  }) => {
    await loginAs(page, await newUser())
    await page.getByLabel('添付する画像を選択').setInputFiles(tinyPng())
    await expect(page.getByAltText('添付画像1')).toBeVisible()

    const body = `画像つき ${Date.now()}`
    const card = await postViaUi(page, body)
    const image = card.getByAltText('投稿画像1')
    await expect(image).toBeVisible()
    // 署名付き URL（MinIO）から実際に画像が読めていること。alt だけでは壊れた URL でも通ってしまう
    await expect.poll(() => image.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0)
    // プレビューは投稿後に消える
    await expect(page.getByAltText('添付画像1')).toHaveCount(0)
  })

  test('境界値: 画像は 4 枚まで添付でき、5 枚目でエラーになる', async ({
    page,
    newUser,
    loginAs,
  }) => {
    await loginAs(page, await newUser())
    const input = page.getByLabel('添付する画像を選択')
    await input.setInputFiles([1, 2, 3, 4].map((i) => tinyPng(`img${i}.png`)))
    await expect(page.getByAltText('添付画像4')).toBeVisible()
    await expect(page.getByRole('button', { name: '画像を添付' })).toBeDisabled()

    // 4 枚の状態で 1 枚取り外し、5 枚（3 + 2）になる選び方をする
    await page.getByRole('button', { name: '添付画像4を取り外す' }).click()
    await input.setInputFiles([tinyPng('a.png'), tinyPng('b.png')])
    await expect(page.getByRole('alert')).toHaveText('画像は4枚まで添付できます')
    await expect(page.getByAltText(/添付画像/)).toHaveCount(3)
  })

  test('同値分割: PNG を名乗るだけの壊れたファイルはサーバーに弾かれ、理由が表示される', async ({
    page,
    newUser,
    loginAs,
  }) => {
    await loginAs(page, await newUser())
    await page.getByLabel('添付する画像を選択').setInputFiles(fakePng())
    const { body, submit } = composer(page)
    await body.fill('壊れた画像')
    await submit.click()
    await expect(page.getByRole('alert')).toBeVisible()
    await expect(postCard(page, '壊れた画像')).toHaveCount(0)
  })
})

test.describe('投稿の編集', () => {
  test('状態遷移: 自分の投稿を編集すると本文が置き換わり「（編集済み）」が付く', async ({
    page,
    api,
    newUser,
    loginAs,
  }) => {
    const me = await newUser()
    await api.createPost(me, '編集前の本文')
    await loginAs(page, me)

    const card = postCard(page, '編集前の本文')
    await card.getByRole('button', { name: '投稿メニュー' }).click()
    await card.getByRole('button', { name: '編集' }).click()
    // 本文を書き換えると元の文言でカードを引けなくなるので、編集中の textarea を持つカードで取り直す
    const editing = editingCard(page, '投稿の本文を編集')
    await editing.getByLabel('投稿の本文を編集').fill('編集後の本文')
    await editing.getByRole('button', { name: '保存', exact: true }).click()

    const edited = postCard(page, '編集後の本文')
    await expect(edited).toBeVisible()
    await expect(edited).toContainText('（編集済み）')
    await expect(postCard(page, '編集前の本文')).toHaveCount(0)
  })

  test('状態遷移: 編集をキャンセルすると元の本文のまま', async ({ page, api, newUser, loginAs }) => {
    const me = await newUser()
    await api.createPost(me, 'キャンセル前の本文')
    await loginAs(page, me)

    const card = postCard(page, 'キャンセル前の本文')
    await card.getByRole('button', { name: '投稿メニュー' }).click()
    await card.getByRole('button', { name: '編集' }).click()
    const editing = editingCard(page, '投稿の本文を編集')
    await editing.getByLabel('投稿の本文を編集').fill('書きかけ')
    await editing.getByRole('button', { name: 'キャンセル' }).click()

    await expect(card).toContainText('キャンセル前の本文')
    await expect(card).not.toContainText('（編集済み）')
    await expect(card.getByLabel('投稿の本文を編集')).toHaveCount(0)
  })
})

test.describe('UC-06 自分の投稿を削除する', () => {
  test('デシジョンテーブル: 確認ダイアログを却下すると残り、承諾すると消える', async ({
    page,
    api,
    newUser,
    loginAs,
  }) => {
    const me = await newUser()
    await api.createPost(me, '削除される投稿')
    await loginAs(page, me)
    const card = postCard(page, '削除される投稿')

    answerConfirm(page, false)
    await card.getByRole('button', { name: '投稿メニュー' }).click()
    await card.getByRole('button', { name: '削除' }).click()
    await expect(card).toBeVisible()

    answerConfirm(page, true)
    await card.getByRole('button', { name: '投稿メニュー' }).click()
    await card.getByRole('button', { name: '削除' }).click()
    await expect(postCard(page, '削除される投稿')).toHaveCount(0)

    // 事後条件: リロードしても消えたまま（手元のリストだけ消したのではない）
    await page.reload()
    await expect(page.getByRole('tab', { name: 'すべて' })).toBeVisible()
    await expect(postCard(page, '削除される投稿')).toHaveCount(0)
  })

  test('デシジョンテーブル: 他人の投稿には編集・削除のメニューが出ない', async ({
    page,
    api,
    newUser,
    loginAs,
  }) => {
    const other = await newUser('他人')
    const me = await newUser('自分')
    await api.createPost(other, '他人の投稿')
    await api.createPost(me, '自分の投稿')
    await loginAs(page, me)

    await expect(postCard(page, '自分の投稿').getByRole('button', { name: '投稿メニュー' })).toBeVisible()
    await expect(postCard(page, '他人の投稿')).toBeVisible()
    await expect(postCard(page, '他人の投稿').getByRole('button', { name: '投稿メニュー' })).toHaveCount(0)
  })
})
