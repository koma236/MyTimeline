// load: 通常負荷でのベースライン。前回の結果と比較するための基準になる計測。
//
// 合否: docs/05 の基準（read / write の p95 < 1 秒、失敗率 < 1 %）。
// 短縮して試すとき: PERF_LOAD_VUS=10 PERF_LOAD_RAMP=30s PERF_LOAD_HOLD=1m bash perf/run.sh run load
import { SLO_THRESHOLDS, toSeconds } from '../lib/config.js';
export { userJourney, uploadJourney } from '../lib/flows.js';

const VUS = Number(__ENV.PERF_LOAD_VUS || 50);
const RAMP = __ENV.PERF_LOAD_RAMP || '2m';
const HOLD = __ENV.PERF_LOAD_HOLD || '10m';

export const options = {
  scenarios: {
    journey: {
      executor: 'ramping-vus',
      exec: 'userJourney',
      startVUs: 0,
      stages: [
        { duration: RAMP, target: VUS },
        { duration: HOLD, target: VUS },
        { duration: '1m', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
    // VU 数ではなく到着率で固定する。応答が遅くなっても投稿の頻度が落ちないので、
    // 「サーバーが遅いと負荷も下がって見かけ上は耐えてしまう」のを避けられる
    upload: {
      executor: 'constant-arrival-rate',
      exec: 'uploadJourney',
      rate: 12,
      timeUnit: '1m',
      // 上昇 + 維持の間だけ流す（下降中は流さない）
      duration: `${toSeconds(RAMP) + toSeconds(HOLD)}s`,
      preAllocatedVUs: 5,
      maxVUs: 20,
    },
  },
  thresholds: {
    ...SLO_THRESHOLDS,
    // 画像つき投稿の基準は docs/05 に無いため暫定値（S3 への保存を含むので通常操作より緩い）
    'http_req_duration{type:upload}': ['p(95)<3000'],
  },
};
