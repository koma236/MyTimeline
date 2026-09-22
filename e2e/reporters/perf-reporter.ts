// tests/perf/ が testInfo.attach した計測値を集め、results/ に JSON と Markdown の表を書く。
// 標準出力にも同じ表を出す。docs/14_e2e_test.md に転記するのは Markdown の方。
import type { FullResult, Reporter, TestCase, TestResult } from '@playwright/test/reporter'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

interface Metric {
  name: string
  unit: string
  values: number[]
  threshold?: number
  note?: string
}

interface Row extends Metric {
  test: string
  median: number
  min: number
  max: number
  pass: boolean | null
}

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 0 ? (sorted[mid - 1]! + sorted[mid]!) / 2 : sorted[mid]!
}

function format(value: number, unit: string): string {
  if (unit === 'ms') return String(Math.round(value))
  if (unit === 'score') return value.toFixed(3)
  return String(Math.round(value * 10) / 10)
}

function timestamp(): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}` +
    `-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  )
}

export default class PerfReporter implements Reporter {
  private readonly rows: Row[] = []

  onTestEnd(test: TestCase, result: TestResult): void {
    for (const attachment of result.attachments) {
      if (!attachment.name.startsWith('perf:') || !attachment.body) continue
      const metric = JSON.parse(attachment.body.toString('utf8')) as Metric
      const med = median(metric.values)
      this.rows.push({
        ...metric,
        test: test.title,
        median: med,
        min: Math.min(...metric.values),
        max: Math.max(...metric.values),
        pass: metric.threshold === undefined ? null : med < metric.threshold,
      })
    }
  }

  onEnd(_result: FullResult): void {
    if (this.rows.length === 0) return

    const lines = [
      '| 計測 | 単位 | 中央値 | 最小 | 最大 | 回数 | 基準（中央値 <） | 合否 |',
      '|---|---|---|---|---|---|---|---|',
      ...this.rows.map(
        (r) =>
          `| ${r.name} | ${r.unit} | ${format(r.median, r.unit)} | ${format(r.min, r.unit)} | ` +
          `${format(r.max, r.unit)} | ${r.values.length} | ` +
          `${r.threshold === undefined ? '記録のみ' : format(r.threshold, r.unit)} | ` +
          `${r.pass === null ? '-' : r.pass ? '合格' : '**不合格**'} |`,
      ),
    ]
    const notes = this.rows.filter((r) => r.note).map((r) => `- ${r.name}: ${r.note}`)
    const markdown = [...lines, '', ...notes, ''].join('\n')

    const here = path.dirname(fileURLToPath(import.meta.url))
    const dir = path.resolve(here, '../results')
    mkdirSync(dir, { recursive: true })
    const prefix = path.join(dir, `${timestamp()}-perf`)
    writeFileSync(`${prefix}.json`, JSON.stringify(this.rows, null, 2))
    writeFileSync(`${prefix}.md`, markdown)

    console.log('\nブラウザ性能の計測結果（中央値で合否）\n')
    console.log(markdown)
    console.log(`結果: ${prefix}.{json,md}`)
  }
}
