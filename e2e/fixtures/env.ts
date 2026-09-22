// テスト全体で共有する接続先。run.sh から環境変数で上書きできる。
export const FRONTEND_PORT = Number(process.env.E2E_FRONTEND_PORT ?? 4173)
/** ブラウザが開く URL（vite preview）。/api は preview のプロキシで backend へ流れる */
export const BASE_URL = process.env.E2E_BASE_URL ?? `http://localhost:${FRONTEND_PORT}`
/** テストがシードデータを投入するときに直接叩く backend */
export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8080'
/** ブラウザ性能の計測で各操作を繰り返す回数（中央値で合否を判定する） */
export const PERF_RUNS = Number(process.env.E2E_PERF_RUNS ?? 5)
