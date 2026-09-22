// 画面要素の取り方をまとめる。フロントに data-testid は足さず、既存の aria-label / ロール /
// 文言で取る（利用者や支援技術が見るものと同じ手がかりで操作する）。
import { expect, type Locator, type Page } from '@playwright/test'

/** 本文を含む投稿カード（タイムライン・プロフィール・投稿詳細で共通の <article>） */
export function postCard(page: Page, body: string): Locator {
  return page.getByRole('article').filter({ hasText: body })
}

/** コメントカード。投稿詳細では投稿本体も <article> なので、本文で絞る */
export const commentCard = postCard

/**
 * 編集中のカード。本文を書き換えると元の文言では引けなくなるため、編集用 textarea の
 * aria-label（'投稿の本文を編集' / 'コメントの本文を編集'）で取る
 */
export function editingCard(page: Page, textareaLabel: string): Locator {
  return page.getByRole('article').filter({ has: page.getByLabel(textareaLabel) })
}

export function likeButton(card: Locator): Locator {
  return card.getByRole('button', { name: /いいね/ })
}

/** タイムライン上の投稿から詳細（SCR-04）へ。時刻リンクとコメントリンクの両方が /posts/:id を指す */
export function detailLink(card: Locator): Locator {
  return card.locator('a[href^="/posts/"]').first()
}

export function timelineTab(page: Page, name: 'すべて' | 'フォロー中'): Locator {
  return page.getByRole('tab', { name })
}

export function composer(page: Page): { body: Locator; submit: Locator; remaining: Locator } {
  const form = page.locator('form').filter({ has: page.getByLabel('投稿の本文') })
  return {
    body: page.getByLabel('投稿の本文'),
    // 「投稿メニュー」「投稿中…」と区別するため exact
    submit: page.getByRole('button', { name: '投稿', exact: true }),
    remaining: form.locator('span').filter({ hasText: /^-?\d+$/ }),
  }
}

/** 画面から投稿し、先頭に現れるまで待つ */
export async function postViaUi(page: Page, body: string): Promise<Locator> {
  const { body: textarea, submit } = composer(page)
  await textarea.fill(body)
  await submit.click()
  const card = postCard(page, body).first()
  await expect(card).toBeVisible()
  return card
}

/** window.confirm を承諾 / 却下する。1 テストで 1 度だけ登録すること（重複登録すると 2 回応答してエラーになる） */
export function answerConfirm(page: Page, accept: boolean): void {
  page.once('dialog', (dialog) => (accept ? dialog.accept() : dialog.dismiss()))
}

/** ヘッダー（ログイン中のみ表示） */
export function header(page: Page): Locator {
  return page.getByRole('banner')
}

export function repeat(char: string, count: number): string {
  return char.repeat(count)
}
