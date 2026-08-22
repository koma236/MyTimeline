import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { vi } from 'vitest'
import { absoluteTime, relativeTime } from './relativeTime'

/**
 * 設計技法: 境界値分析。
 * 「たった今 / 分 / 時間 / 日 / 年月日」の 5 つの同値クラスの境界（60 秒・60 分・24 時間・7 日）を、
 * その直前と直後の 1 秒で挟んで確かめる。
 *
 * バックエンドの LocalDateTime はタイムゾーンを持たない文字列なので、
 * 入力も現在時刻もローカル時刻で組み立ててズレを作らない。
 */
const NOW = new Date(2026, 7, 22, 12, 0, 0) // 2026-08-22 12:00:00（ローカル）

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** NOW から ms ミリ秒前の時刻を、バックエンドと同じ "YYYY-MM-DDTHH:mm:ss" 形式にする */
function before(ms: number): string {
  const date = new Date(NOW.getTime() - ms)
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

const SECOND = 1_000
const MINUTE = 60 * SECOND
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

describe('relativeTime', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it.each([
    // [経過時間, 期待値, 境界の説明]
    [0, 'たった今', '同時刻'],
    [59 * SECOND, 'たった今', '1 分未満の上端'],
    [60 * SECOND, '1分前', '1 分ちょうど'],
    [59 * MINUTE + 59 * SECOND, '59分前', '1 時間未満の上端'],
    [60 * MINUTE, '1時間前', '1 時間ちょうど'],
    [23 * HOUR + 59 * MINUTE, '23時間前', '1 日未満の上端'],
    [24 * HOUR, '1日前', '1 日ちょうど'],
    [6 * DAY + 23 * HOUR, '6日前', '7 日未満の上端'],
  ])('境界値: %i ms 前 → "%s"（%s）', (elapsed, expected) => {
    expect(relativeTime(before(elapsed))).toBe(expected)
  })

  it('境界値: 7 日ちょうどからは相対表記をやめて年月日にする', () => {
    expect(relativeTime(before(7 * DAY))).toBe('2026年8月15日')
  })

  it('年月日は月・日をゼロ埋めしない（"8月5日"）', () => {
    vi.setSystemTime(new Date(2026, 7, 22))
    expect(relativeTime('2026-08-05T00:00:00')).toBe('2026年8月5日')
  })

  it('エラー推測: 未来の時刻（サーバーとの時計ずれ）は負の差分になるが "たった今" に丸める', () => {
    expect(relativeTime(before(-30 * SECOND))).toBe('たった今')
  })
})

describe('absoluteTime', () => {
  it('日本語ロケールの日時文字列にする', () => {
    const text = absoluteTime('2026-08-22T09:05:00')
    expect(text).toContain('2026')
    expect(text).toContain('8')
    expect(text).toContain('22')
    expect(text).toContain('9:05')
  })
})
