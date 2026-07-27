/**
 * 投稿日時を「3分前」のような相対表記にする（mock/js/ui.js の relativeTime 相当）。
 *
 * バックエンドの LocalDateTime はタイムゾーンを持たない文字列で返るため、
 * new Date() はブラウザのローカル時刻として解釈する。サーバーとクライアントが
 * 同じタイムゾーンで動いている前提であり、docker-compose.yml では
 * db / backend の TZ を Asia/Tokyo に揃えてこれを満たしている。
 */
export function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()

  const minutes = Math.floor(diff / 60_000)
  if (minutes < 1) return 'たった今'
  if (minutes < 60) return `${minutes}分前`

  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}時間前`

  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}日前`

  const date = new Date(iso)
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

/** ツールチップなどで使う絶対時刻。 */
export function absoluteTime(iso: string): string {
  return new Date(iso).toLocaleString('ja-JP')
}
