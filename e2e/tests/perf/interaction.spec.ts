// 操作の反映時間。「ボタンを押してから画面に結果が出るまで」をブラウザ内の時刻で測る。
// 合否は docs/05_nonfunctional.md「通常操作（投稿・いいね・コメント等）は 1 秒以内に反映」。
// 検索だけは意図的な debounce（300 ms）を含むので 1.3 秒。
import { expect, test } from '../../fixtures/test'
import { detailLink, likeButton, postCard, timelineTab } from '../../fixtures/helpers'
import { measureInteraction, measureRepeated } from '../../fixtures/perf'

test.describe('操作の反映時間', () => {
  test('投稿: 送信 → タイムラインの先頭に表示', async ({ page, newUser, loginAs }, testInfo) => {
    await loginAs(page, await newUser('perf'))
    await measureRepeated(testInfo, { name: '投稿の反映', unit: 'ms', threshold: 1000 }, async (i) => {
      const body = `計測投稿 ${i} ${Date.now()}`
      await page.getByLabel('投稿の本文').fill(body)
      return measureInteraction(
        page,
        () => page.getByRole('button', { name: '投稿', exact: true }).click(),
        (expected) => document.querySelector('article')?.textContent?.includes(expected) ?? false,
        body,
      )
    })
  })

  test('いいね: クリック → ♥ と件数の反映', async ({ page, api, newUser, loginAs }, testInfo) => {
    const me = await newUser('perf')
    await api.createPost(me, 'いいね計測の投稿')
    await loginAs(page, me)
    const button = likeButton(postCard(page, 'いいね計測の投稿'))
    await measureRepeated(testInfo, { name: 'いいねの反映', unit: 'ms', threshold: 1000 }, (i) =>
      measureInteraction(
        page,
        () => button.click(),
        (expected) =>
          document.querySelector('article button[aria-pressed]')?.getAttribute('aria-pressed') === expected,
        i % 2 === 0 ? 'true' : 'false',
      ),
    )
  })

  test('タブ切替: すべて ⇄ フォロー中（各 25 件）', async ({ page, api, newUser, loginAs }, testInfo) => {
    const me = await newUser('perf')
    const other = await newUser('perf')
    await api.createPosts(me, 25, 'タブ計測 自分の投稿')
    await api.createPosts(other, 25, 'タブ計測 他人の投稿')
    await loginAs(page, me)
    await expect(page.getByRole('article').first()).toContainText('他人の投稿')

    await measureRepeated(testInfo, { name: 'タブ切替の反映', unit: 'ms', threshold: 1000 }, (i) => {
      const toFollowing = i % 2 === 0
      return measureInteraction(
        page,
        () => timelineTab(page, toFollowing ? 'フォロー中' : 'すべて').click(),
        (expected) => document.querySelector('article')?.textContent?.includes(expected) ?? false,
        toFollowing ? 'タブ計測 自分の投稿' : 'タブ計測 他人の投稿',
      )
    })
  })

  test('無限スクロール: 末尾到達 → 次の 20 件の表示', async ({ page, api, newUser, loginAs }, testInfo) => {
    const me = await newUser('perf')
    await api.createPosts(me, 60, 'スクロール計測の投稿')
    await loginAs(page, me)

    await measureRepeated(testInfo, { name: '無限スクロールの次ページ', unit: 'ms', threshold: 1000 }, async () => {
      await page.goto('/')
      await expect(page.getByRole('article')).toHaveCount(20)
      return measureInteraction(
        page,
        () => page.getByRole('article').last().scrollIntoViewIfNeeded(),
        (expected) => document.querySelectorAll('article').length >= expected,
        40,
      )
    })
  })

  test('ユーザー検索: 入力 → 結果表示（debounce 300 ms を含む）', async ({ page, newUser, loginAs }, testInfo) => {
    const me = await newUser('perf')
    const target = await newUser('perf')
    await loginAs(page, me)

    await measureRepeated(testInfo, { name: 'ユーザー検索の結果表示', unit: 'ms', threshold: 1300, note: 'SearchPage の debounce 300 ms を含む' }, async () => {
      await page.goto('/search')
      await expect(page.getByPlaceholder('ユーザー名で検索...')).toBeVisible()
      // 検索前は新着ユーザー一覧に相手が既に出ているので、「1 件に絞り込まれた」ことを終了条件にする
      await expect(page.getByRole('article').first()).toBeVisible()
      return measureInteraction(
        page,
        () => page.getByPlaceholder('ユーザー名で検索...').fill(target.username),
        (expected) => {
          const rows = document.querySelectorAll('article')
          return rows.length === 1 && (rows[0]?.textContent?.includes(expected) ?? false)
        },
        `@${target.username}`,
      )
    })
  })

  test('投稿詳細へ遷移: クリック → 投稿とコメント 5 件の表示', async ({ page, api, newUser, loginAs }, testInfo) => {
    const me = await newUser('perf')
    const post = await api.createPost(me, '詳細計測の投稿')
    for (let i = 1; i <= 5; i += 1) await api.createComment(me, post.id, `詳細計測のコメント ${i}`)
    await loginAs(page, me)

    await measureRepeated(testInfo, { name: '投稿詳細への遷移', unit: 'ms', threshold: 1000 }, async () => {
      await page.goto('/')
      const card = postCard(page, '詳細計測の投稿')
      await expect(card).toBeVisible()
      return measureInteraction(
        page,
        () => detailLink(card).click(),
        (expected) =>
          location.pathname.startsWith('/posts/') && (document.body.textContent?.includes(expected) ?? false),
        '詳細計測のコメント 5',
      )
    })
  })
})
