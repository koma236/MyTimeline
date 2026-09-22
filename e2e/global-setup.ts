// テスト開始前に backend が応答することだけ確かめる。
// 「backend が e2e 用 DB を向いているか」は Docker を見ないと分からないので run.sh 側が担当する。
import { API_URL } from './fixtures/env'

export default async function globalSetup(): Promise<void> {
  const url = `${API_URL}/actuator/health/readiness`
  try {
    const response = await fetch(url)
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
  } catch (caught) {
    throw new Error(
      `backend が応答しません（${url}: ${String(caught)}）。` +
        '先に bash e2e/run.sh up を実行してください',
    )
  }
}
