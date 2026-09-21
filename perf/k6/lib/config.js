// 全テストで共有する設定。シードデータ（perf/seed/seed.sql）と対になっているので、
// ユーザー名の形式やパスワードを変えるときは両方を直すこと。

export const BASE_URL = __ENV.BASE_URL || 'http://backend:8080';

// シードしたユーザー数。run.sh が seed と同じ値（PERF_USERS）を渡す
export const USER_COUNT = Number(__ENV.PERF_USERS || 1000);
export const POST_COUNT = Number(__ENV.PERF_POSTS || 100000);

// perf 用 DB にしか存在しないユーザーの共通パスワード
export const PASSWORD = 'PerfTest1234';

// ユーザープールの末尾は用途を分けて予約してある。
//   末尾 5 人        … ほぼ全員をフォローするヘビーユーザー（TBD-13 の計測用）
//   その手前 20 人   … 認証フロー専用（refresh / logout でセッションを壊しても他に影響しない）
//   それより前       … 通常の仮想ユーザー（VU）に 1 人ずつ割り当てる
export const HEAVY_USER_COUNT = 5;
export const AUTH_FLOW_USER_COUNT = 20;
export const NORMAL_USER_COUNT = USER_COUNT - HEAVY_USER_COUNT - AUTH_FLOW_USER_COUNT;

if (NORMAL_USER_COUNT < 1) {
  throw new Error(`PERF_USERS=${USER_COUNT} は少なすぎます（予約分 25 人より多くしてください）`);
}

/** seed.sql は id を明示して投入しているので「perf_user_00042 の id は 42」が成り立つ。 */
export function usernameFor(userId) {
  return `perf_user_${String(userId).padStart(5, '0')}`;
}

// docs/05_nonfunctional.md の数値をそのまま合否基準にする。
//   通常操作は 1 秒以内 / p95 1 秒 / 5xx 率 1 %（警告）
// 認証（type:auth）は BCrypt が意図的に重いので、読み書きとは別枠で集計する。
export const SLO_THRESHOLDS = {
  'http_req_duration{type:read}': ['p(95)<1000'],
  'http_req_duration{type:write}': ['p(95)<1000'],
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
};
