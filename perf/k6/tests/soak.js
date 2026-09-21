// soak: 長時間流し続けて、時間とともに悪化する問題を探す。
//   - JVM ヒープが増え続けないか（docker-stats.csv のメモリ、prometheus の jvm_memory_used_bytes）
//   - DB 接続が漏れていないか（hikaricp_connections_active が戻るか）
//   - 境界値: アクセストークンの 15 分境界をまたいでも取り直せるか
//     → 集計の perf_login_token_age が 1 以上、perf_login_after_401 が 0 なら期待どおり
//
// 合否: load と同じ（docs/05 の基準）。
// 短縮して試すとき（15 分境界だけ確かめる）: PERF_SOAK_VUS=2 PERF_SOAK_DURATION=16m bash perf/run.sh run soak
import { SLO_THRESHOLDS } from '../lib/config.js';
export { userJourney } from '../lib/flows.js';

export const options = {
  scenarios: {
    journey: {
      executor: 'constant-vus',
      exec: 'userJourney',
      vus: Number(__ENV.PERF_SOAK_VUS || 30),
      duration: __ENV.PERF_SOAK_DURATION || '60m',
    },
  },
  thresholds: SLO_THRESHOLDS,
};
