// focus: 既知の懸念（docs/08 の TBD）を 1 つずつ切り出し、条件だけを変えた対で比べる。
//
// 各区間は同じ到着率（既定 20 req/s）で 40 秒ずつ、順番に流す。VU 数ではなく到着率で固定するのは、
// 遅いエンドポイントほどリクエスト数が減ってしまい、対の比較が不公平になるのを避けるため。
// 合否は付けない。区間ごとの p95 を docs/13 に記録し、改善の前後で比べる。
//
//   境界値  timeline_limit20 / timeline_limit50 … 既定値と上限。1 回あたりの署名付き URL 生成数が変わる（TBD-09）
//   境界値  following_normal / following_heavy  … フォロー数 約 50 人と 約 1,000 人。IN (...) の長さが変わる（TBD-13）
//   同値分割 search_hit / search_nohit           … ヒットあり / なし。どちらも全件走査（TBD-12）
//   login                                       … BCrypt。CPU 律速なので到着率を下げて測る（TBD-17 の前提確認）
import exec from 'k6/execution';
import { usernameFor } from '../lib/config.js';
import { ensureSession, heavyUserId, login, normalUserIdForThisVu } from '../lib/auth.js';
import * as api from '../lib/api.js';

const RATE = Number(__ENV.PERF_FOCUS_RATE || 20);
const LOGIN_RATE = Number(__ENV.PERF_FOCUS_LOGIN_RATE || 5);
const DURATION_S = Number(__ENV.PERF_FOCUS_DURATION_S || 40);
const GAP_S = 5;

const SEGMENTS = [
  { name: 'timeline_limit20', exec: 'timelineLimit20', rate: RATE, type: 'read' },
  { name: 'timeline_limit50', exec: 'timelineLimit50', rate: RATE, type: 'read' },
  { name: 'following_normal', exec: 'followingNormal', rate: RATE, type: 'read' },
  { name: 'following_heavy', exec: 'followingHeavy', rate: RATE, type: 'read' },
  { name: 'search_hit', exec: 'searchHit', rate: RATE, type: 'read' },
  { name: 'search_nohit', exec: 'searchNoHit', rate: RATE, type: 'read' },
  { name: 'login', exec: 'loginOnly', rate: LOGIN_RATE, type: 'auth' },
];

const scenarios = {};
const thresholds = {};
SEGMENTS.forEach((segment, index) => {
  scenarios[segment.name] = {
    executor: 'constant-arrival-rate',
    exec: segment.exec,
    startTime: `${index * (DURATION_S + GAP_S)}s`,
    duration: `${DURATION_S}s`,
    rate: segment.rate,
    timeUnit: '1s',
    preAllocatedVUs: 20,
    maxVUs: 100,
  };
  // type で絞るのは、区間の頭で走るログイン（type:auth）を読み取りの数値に混ぜないため。
  // 必ず満たす閾値にして、区間ごとの系列を集計結果に出す
  thresholds[`http_req_duration{scenario:${segment.name},type:${segment.type}}`] = ['p(95)>=0'];
});
// 到着率に VU が追いつかず捨てられた反復。0 でなければ、その区間の数値は「その到着率では捌けなかった」と読む
thresholds.dropped_iterations = ['count>=0'];

export const options = { scenarios, thresholds };

export function timelineLimit20() {
  api.getTimeline(ensureSession(), 'all', { limit: 20 });
}

export function timelineLimit50() {
  api.getTimeline(ensureSession(), 'all', { limit: 50 });
}

export function followingNormal() {
  api.getTimeline(ensureSession(normalUserIdForThisVu()), 'following');
}

export function followingHeavy() {
  api.getTimeline(ensureSession(heavyUserId(exec.vu.idInTest)), 'following');
}

export function searchHit() {
  api.searchUsers(ensureSession(), 'perf_user_0001');
}

export function searchNoHit() {
  api.searchUsers(ensureSession(), 'nohit-zzzz');
}

export function loginOnly() {
  login(usernameFor(normalUserIdForThisVu()));
}
