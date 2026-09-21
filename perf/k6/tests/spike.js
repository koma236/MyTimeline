// spike: 利用者が急増したときの挙動と、収まった後に元の性能へ戻るかを確かめる。
//
// 3 つのシナリオを時間をずらして流し、k6 が自動で付ける scenario タグで区間ごとに集計する。
//   before … 平常時（比較の基準）
//   spike  … 10 秒で急増して 1 分維持。ログインが集中するので BCrypt で CPU が埋まる
//   after  … 平常時に戻した後。ここが合否の対象
// 合否: after 区間が docs/05 の基準（p95 < 1 秒、失敗率 < 1 %）に戻っていること。
// spike 区間そのものは記録のみ（急増中に遅くなるのは想定内で、知りたいのは回復するか）。
export { userJourney } from '../lib/flows.js';

const BASE_VUS = Number(__ENV.PERF_SPIKE_BASE_VUS || 10);
const PEAK_VUS = Number(__ENV.PERF_SPIKE_PEAK_VUS || 300);

export const options = {
  scenarios: {
    before: {
      executor: 'constant-vus',
      exec: 'userJourney',
      vus: BASE_VUS,
      duration: '1m',
    },
    spike: {
      executor: 'ramping-vus',
      exec: 'userJourney',
      startTime: '1m',
      startVUs: BASE_VUS,
      stages: [
        { duration: '10s', target: PEAK_VUS },
        { duration: '1m', target: PEAK_VUS },
        { duration: '10s', target: BASE_VUS },
      ],
      gracefulRampDown: '10s',
      gracefulStop: '10s',
    },
    after: {
      executor: 'constant-vus',
      exec: 'userJourney',
      startTime: '2m40s',
      vus: BASE_VUS,
      duration: '2m',
    },
  },
  thresholds: {
    'http_req_duration{scenario:after,type:read}': ['p(95)<1000'],
    'http_req_duration{scenario:after,type:write}': ['p(95)<1000'],
    'http_req_failed{scenario:after}': ['rate<0.01'],
    // 以下は記録用（必ず満たす閾値で、集計結果に系列を出す）
    'http_req_duration{scenario:before,type:read}': ['p(95)>=0'],
    'http_req_duration{scenario:spike,type:read}': ['p(95)>=0'],
    'http_req_duration{scenario:spike,type:write}': ['p(95)>=0'],
    'http_req_duration{scenario:spike,type:auth}': ['p(95)>=0'],
    'http_req_failed{scenario:spike}': ['rate>=0'],
  },
};
