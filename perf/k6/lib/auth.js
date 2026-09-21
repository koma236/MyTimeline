// VU ごとのログイン状態の管理。
//
// このアプリのリフレッシュトークンはローテーションし、使用済みトークンを再提示すると
// 「盗用」とみなして、そのユーザーの全セッションを失効させる（RefreshTokenService）。
// 負荷テストで 1 ユーザーを複数 VU が共有して refresh を並行に叩くと、テスト自身が
// セッションを壊して 401 の山を作ってしまう。そのため次の方針を取る。
//   - VU 1 つにシードユーザー 1 人を割り当てる
//   - 通常フローでは refresh を使わず、期限が近づいたらログインし直す
//   - refresh / logout は認証フロー専用ユーザーで、1 VU の中で直列にだけ実行する
import exec from 'k6/execution';
import http from 'k6/http';
import { check, fail } from 'k6';
import {
  AUTH_FLOW_USER_COUNT,
  BASE_URL,
  HEAVY_USER_COUNT,
  NORMAL_USER_COUNT,
  PASSWORD,
  USER_COUNT,
  usernameFor,
} from './config.js';

// アクセストークンの有効期限は 15 分。余裕を持って 12 分で取り直す（soak で 15 分境界をまたぐため）
const TOKEN_MAX_AGE_MS = 12 * 60 * 1000;

// k6 は VU ごとに独立した JS ランタイムを持つので、モジュール変数は VU ごとの状態になる
let session = null;

export function login(username) {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ identifier: username, password: PASSWORD }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { type: 'auth', name: 'POST /api/auth/login' },
    },
  );
  const ok = check(res, { 'login: 200': (r) => r.status === 200 });
  if (!ok) {
    // ログインできなければ以降は全部 401 になるだけなので、この反復は打ち切る
    fail(`login failed for ${username}: status=${res.status} body=${res.body}`);
  }
  const body = res.json();
  return {
    username,
    userId: body.user.id,
    accessToken: body.accessToken,
    obtainedAt: Date.now(),
  };
}

/** この VU に割り当てられた通常ユーザーの id（1 始まり）。VU 数がプールを超えたら循環する。 */
export function normalUserIdForThisVu() {
  return ((exec.vu.idInTest - 1) % NORMAL_USER_COUNT) + 1;
}

/** 認証フロー専用ユーザーの id。通常ユーザー・ヘビーユーザーとは重ならない。 */
export function authFlowUserIdForThisVu() {
  const base = USER_COUNT - HEAVY_USER_COUNT - AUTH_FLOW_USER_COUNT;
  return base + ((exec.vu.idInTest - 1) % AUTH_FLOW_USER_COUNT) + 1;
}

/** ヘビーユーザー（ほぼ全員をフォロー）の id。index は 0 始まり。 */
export function heavyUserId(index) {
  return USER_COUNT - HEAVY_USER_COUNT + (index % HEAVY_USER_COUNT) + 1;
}

/** ログイン済みのセッションを返す。未ログイン・期限間近ならログインし直す。 */
export function ensureSession(userId = normalUserIdForThisVu()) {
  const username = usernameFor(userId);
  const stale = session && Date.now() - session.obtainedAt > TOKEN_MAX_AGE_MS;
  if (!session || stale || session.username !== username) {
    session = login(username);
  }
  return session;
}

/** 401 を受けたときに呼ぶ。次の ensureSession でログインし直させる。 */
export function invalidateSession() {
  session = null;
}

export function bearer(s) {
  return { Authorization: `Bearer ${s.accessToken}` };
}
