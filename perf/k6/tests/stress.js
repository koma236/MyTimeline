// stress: 負荷を段階的に上げて、どこで・何が原因で限界を迎えるかを調べる。
//
// 合格・不合格を決めるテストではない。「p95 が 1 秒を超えた段」「失敗が出始めた段」を
// レポートから読み取り、docker-stats と prometheus のスナップショットで原因を切り分ける。
// 失敗率が 5 %（docs/05 の Critical 閾値）を超えたら、それ以上続けても意味がないので自動で中断する。
// 境界値: 限界点が分かったら、その前後の VU 数で load を流して境界を確かめる。
export { userJourney } from '../lib/flows.js';

// 既定の段は 2026-09-21 の実測で決めた。当初の 50〜400 VU では backend の CPU が平均 22 % に留まり、
// 限界を探すテストになっていなかった（docs/13 の 15.9）。VU 数がシードユーザー数を超えると
// 同じユーザーを複数 VU が使うが、通常フローは refresh を呼ばないのでセッションは壊れない
const STEPS = (__ENV.PERF_STRESS_STEPS || '200,400,800,1600').split(',').map(Number);
const STEP_HOLD = __ENV.PERF_STRESS_HOLD || '3m';

const stages = [];
for (const target of STEPS) {
  stages.push({ duration: '30s', target });
  stages.push({ duration: STEP_HOLD, target });
}
stages.push({ duration: '30s', target: 0 });

export const options = {
  scenarios: {
    journey: {
      executor: 'ramping-vus',
      exec: 'userJourney',
      startVUs: 0,
      stages,
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '30s' }],
    // 段ごとの数値はレポートで見る。ここは集計結果に系列を出すためだけの、必ず満たす閾値
    'http_req_duration{type:read}': ['p(95)>=0'],
    'http_req_duration{type:write}': ['p(95)>=0'],
    'http_req_duration{type:auth}': ['p(95)>=0'],
  },
};
