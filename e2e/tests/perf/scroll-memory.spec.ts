// 長いタイムラインを読み切ったときのブラウザ側の資源。無限スクロールは読んだ分を DOM に
// 積み上げるので、投稿 1 件あたりのノード数とヒープの増え方を記録して前回と比較する。
import { expect, test } from '../../fixtures/test'
import { timelineTab } from '../../fixtures/helpers'
import { readRuntimeMetrics, recordValue } from '../../fixtures/perf'

const TOTAL = 200

test('200 件をスクロールで読み切ったときの DOM ノード数と JS ヒープ', async ({
  page,
  api,
  newUser,
  loginAs,
}, testInfo) => {
  const me = await newUser('perf')
  await api.createPosts(me, TOTAL, 'メモリ計測の投稿')
  await loginAs(page, me)
  await timelineTab(page, 'フォロー中').click()
  const articles = page.getByRole('article')
  await expect(articles).toHaveCount(20)
  const before = await readRuntimeMetrics(page)

  let count = 20
  while (count < TOTAL) {
    await articles.last().scrollIntoViewIfNeeded()
    await expect.poll(() => articles.count(), { timeout: 5_000 }).toBeGreaterThan(count)
    count = await articles.count()
  }
  await expect(articles).toHaveCount(TOTAL)
  const after = await readRuntimeMetrics(page)

  await recordValue(testInfo, { name: 'JS ヒープ（20 件表示時）', unit: 'MB' }, before.jsHeapUsedMB)
  await recordValue(testInfo, { name: 'JS ヒープ（200 件表示後）', unit: 'MB', threshold: 100 }, after.jsHeapUsedMB)
  await recordValue(testInfo, { name: 'DOM ノード数（20 件表示時）', unit: 'count' }, before.domNodes)
  await recordValue(testInfo, { name: 'DOM ノード数（200 件表示後）', unit: 'count' }, after.domNodes)
  await recordValue(
    testInfo,
    { name: '投稿 1 件あたりの DOM ノード増加', unit: 'count', note: '(200 件時 − 20 件時) / 180' },
    (after.domNodes - before.domNodes) / (TOTAL - 20),
  )
})
