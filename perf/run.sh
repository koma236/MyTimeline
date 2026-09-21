#!/usr/bin/env bash
# パフォーマンステストの実行スクリプト。任意のタイミングで手動実行する（CI では動かさない）。
#
#   bash perf/run.sh up                  perf 用 DB・バケットを作り、backend を perf 用設定で起動
#   bash perf/run.sh seed                シードデータを投入（何度流しても同じ状態になる）
#   bash perf/run.sh run <種別>          k6 を実行（種別 = perf/k6/tests/ のファイル名。例: smoke）
#   bash perf/run.sh down [--keep-data]  backend を開発用に戻し、テストデータを削除
#   bash perf/run.sh clean-results       perf/results/ のレポートを削除
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# 削除対象の名前はここに固定で持つ。.env から組み立てると、設定ミスで開発用を消しかねない。
# 変えるときは perf/docker-compose.perf.yml と perf/seed/seed.sql も合わせること
readonly PERF_DB="mytimeline_perf"
readonly PERF_BUCKET="mytimeline-images-perf"
readonly BACKEND_CONTAINER="mytimeline_backend"
readonly DB_CONTAINER="mytimeline_db"
readonly MINIO_CONTAINER="mytimeline_minio"
readonly BASE_URL="http://localhost:8080"

DC_DEV=(docker compose)
DC_PERF=(docker compose -f docker-compose.yml -f perf/docker-compose.perf.yml)

log()  { printf '\033[1;34m[perf]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[perf]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[perf]\033[0m %s\n' "$*" >&2; exit 1; }

# .env を source しない。JWT_SECRET などに記号が入っているとシェルが解釈して壊れるため、
# 必要なキーだけを文字列として取り出す
env_get() {
  local key="$1" default="${2:-}" value=""
  if [[ -f .env ]]; then
    value="$(grep -E "^${key}=" .env | tail -1 | cut -d= -f2- | sed -E "s/^[\"']//; s/[\"']\$//" || true)"
  fi
  printf '%s' "${value:-$default}"
}

DEV_DB="$(env_get DB_NAME)"
DB_USER="$(env_get DB_USER)"
DEV_BUCKET="$(env_get S3_BUCKET mytimeline-images)"

guard_names() {
  [[ -f .env ]] || die ".env がありません。README の手順で作成してください"
  [[ -n "$DEV_DB" && -n "$DB_USER" ]] || die ".env に DB_NAME / DB_USER がありません"
  # ここを通すと down が開発データを消してしまう。最重要のガード
  [[ "$DEV_DB" != "$PERF_DB" ]] || die ".env の DB_NAME が perf 用 DB 名（${PERF_DB}）と同じです。中断します"
  [[ "$DEV_BUCKET" != "$PERF_BUCKET" ]] || die ".env の S3_BUCKET が perf 用バケット名（${PERF_BUCKET}）と同じです。中断します"
}

psql_dev()  { docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DEV_DB" "$@"; }
psql_perf() { docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$PERF_DB" "$@"; }

# minio イメージには mc が同梱されている。認証情報はコンテナ自身の環境変数から取るので、
# このスクリプトが秘密情報を持ち回らずに済む
minio_mc() {
  docker exec "$MINIO_CONTAINER" sh -c \
    'mc alias set perf http://localhost:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc "$@"' mc "$@"
}

perf_db_exists() {
  [[ "$(psql_dev -tAc "SELECT 1 FROM pg_database WHERE datname = '$PERF_DB'")" == "1" ]]
}

# 動いている backend がどの DB を見ているか。コンテナが無ければ空文字
backend_db_name() {
  docker inspect "$BACKEND_CONTAINER" --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null \
    | grep -E '^DB_NAME=' | cut -d= -f2- || true
}

wait_ready() {
  log "backend の起動を待っています（最大 180 秒）..."
  local i
  for i in $(seq 1 90); do
    if curl -fs "$BASE_URL/actuator/health/readiness" >/dev/null 2>&1; then
      log "backend が応答しました"
      return 0
    fi
    sleep 2
  done
  "${DC_DEV[@]}" logs --tail 40 backend >&2 || true
  die "backend が起動しませんでした。上のログを確認してください"
}

cmd_up() {
  guard_names
  log "db と minio を起動します"
  "${DC_DEV[@]}" up -d --wait db minio

  if perf_db_exists; then
    log "perf 用 DB（${PERF_DB}）は既にあります"
  else
    log "perf 用 DB（${PERF_DB}）を作成します"
    psql_dev -c "CREATE DATABASE $PERF_DB" >/dev/null
  fi

  log "perf 用バケット（${PERF_BUCKET}）を用意します"
  minio_mc mb --ignore-existing "perf/$PERF_BUCKET" >/dev/null

  # --build: 計測するのは「いまチェックアウトしているコード」でなければ意味がない
  log "backend を perf 用設定でビルド・起動します（テーブルは Flyway が作ります）"
  "${DC_PERF[@]}" up -d --build backend
  wait_ready

  [[ "$(backend_db_name)" == "$PERF_DB" ]] || die "backend が $PERF_DB を向いていません"
  log "準備完了。次は bash perf/run.sh seed"
  warn "backend は perf 用 DB を向いています。開発に戻る前に必ず bash perf/run.sh down を実行してください"
}

cmd_seed() {
  guard_names
  perf_db_exists || die "perf 用 DB がありません。先に bash perf/run.sh up を実行してください"
  [[ "$(psql_perf -tAc "SELECT to_regclass('public.users') IS NOT NULL")" == "t" ]] \
    || die "perf 用 DB にテーブルがありません。bash perf/run.sh up で backend を起動し、Flyway を走らせてください"

  log "シードデータを投入します（既存の perf データは消えます）"
  local started=$SECONDS
  psql_perf \
    -v users="${PERF_USERS:-1000}" \
    -v posts="${PERF_POSTS:-100000}" \
    -v follows_per_user="${PERF_FOLLOWS_PER_USER:-50}" \
    -v likes="${PERF_LIKES:-300000}" \
    -v comments="${PERF_COMMENTS:-200000}" \
    < perf/seed/seed.sql
  log "完了（$((SECONDS - started)) 秒）。次は bash perf/run.sh run smoke"
}

# /actuator/prometheus は JWT 必須（TBD-16）なので、シードユーザーでログインして取得する
snapshot_metrics() {
  local out="$1" token
  token="$(curl -s -X POST "$BASE_URL/api/auth/login" -H 'Content-Type: application/json' \
    -d '{"identifier":"perf_user_00001","password":"PerfTest1234"}' \
    | sed -nE 's/.*"accessToken":"([^"]+)".*/\1/p')"
  if [[ -z "$token" ]]; then
    warn "メトリクス取得用のログインに失敗しました（seed 済みですか？）。スナップショットは省略します"
    return 0
  fi
  curl -s -H "Authorization: Bearer $token" "$BASE_URL/actuator/prometheus" > "$out" || true
}

cmd_run() {
  local kind="${1:-}"
  [[ -n "$kind" ]] || die "種別を指定してください。例: bash perf/run.sh run smoke"
  [[ -f "perf/k6/tests/$kind.js" ]] || die "perf/k6/tests/$kind.js がありません"
  guard_names

  # 開発用 DB を向いた backend に負荷をかけると、開発データに大量の書き込みが入ってしまう
  local current
  current="$(backend_db_name)"
  [[ "$current" == "$PERF_DB" ]] \
    || die "backend が perf 用 DB を向いていません（現在: ${current:-未起動}）。先に bash perf/run.sh up を実行してください"
  curl -fs "$BASE_URL/actuator/health/readiness" >/dev/null || die "backend が応答しません"

  mkdir -p perf/results
  local run_id
  run_id="$(date +%Y%m%d-%H%M%S)-$kind"
  local prefix="perf/results/$run_id"
  log "実行 ID: ${run_id}（ライブ表示: http://localhost:5665）"

  snapshot_metrics "$prefix-prometheus-before.txt"

  # サーバー側の CPU・メモリを 5 秒ごとに採取する。k6 の数値だけでは「なぜ遅いか」が分からない
  echo "timestamp,container,cpu_percent,mem_usage,mem_percent" > "$prefix-docker-stats.csv"
  (
    while true; do
      docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}}' \
        "$BACKEND_CONTAINER" "$DB_CONTAINER" 2>/dev/null \
        | sed "s/^/$(date +%H:%M:%S),/" >> "$prefix-docker-stats.csv" || true
      sleep 5
    done
  ) &
  local sampler=$!
  trap 'kill "$sampler" 2>/dev/null || true' EXIT

  # k6 イメージの既定ユーザーでは、Linux だとマウントした results に書けない。実行者の uid で動かす
  local code=0
  "${DC_PERF[@]}" run --rm --service-ports --user "$(id -u):$(id -g)" \
    -e K6_WEB_DASHBOARD_EXPORT="/results/$run_id-report.html" \
    -e PERF_USERS="${PERF_USERS:-1000}" \
    -e PERF_POSTS="${PERF_POSTS:-100000}" \
    k6 run --summary-export "/results/$run_id-summary.json" "/scripts/tests/$kind.js" || code=$?

  kill "$sampler" 2>/dev/null || true
  # wait で回収しないと、bash が「Terminated」というジョブ終了通知を出力に混ぜる
  wait "$sampler" 2>/dev/null || true
  trap - EXIT
  snapshot_metrics "$prefix-prometheus-after.txt"

  log "結果: $prefix-*"
  if [[ $code -eq 0 ]]; then
    log "合格（すべての閾値を満たしました）"
  else
    warn "不合格または異常終了（k6 の終了コード: ${code}。99 は閾値違反）"
  fi
  warn "計測が終わったら bash perf/run.sh down で開発用の状態に戻してください"
  return "$code"
}

cmd_down() {
  local keep=0
  [[ "${1:-}" == "--keep-data" ]] && keep=1
  guard_names

  # 順序が大事: 先に backend を開発用に戻して perf 用 DB への接続を切ってから DROP する
  log "backend を開発用設定で作り直します"
  "${DC_DEV[@]}" up -d backend
  wait_ready
  [[ "$(backend_db_name)" == "$DEV_DB" ]] || die "backend が開発用 DB（${DEV_DB}）に戻っていません"

  if [[ $keep -eq 1 ]]; then
    log "--keep-data: perf 用 DB とバケットは残しました"
    return 0
  fi

  log "perf 用 DB（${PERF_DB}）を削除します"
  psql_dev -c "DROP DATABASE IF EXISTS $PERF_DB WITH (FORCE)" >/dev/null

  log "perf 用バケット（${PERF_BUCKET}）を削除します"
  minio_mc rb --force "perf/$PERF_BUCKET" >/dev/null 2>&1 || log "バケットは既にありません"

  log "後片付け完了。開発用 DB（${DEV_DB}）とバケット（${DEV_BUCKET}）には触れていません"
}

cmd_clean_results() {
  find perf/results -mindepth 1 ! -name .gitkeep -delete
  log "perf/results/ を空にしました"
}

case "${1:-}" in
  up)            cmd_up ;;
  seed)          cmd_seed ;;
  run)           shift; cmd_run "$@" ;;
  down)          shift; cmd_down "$@" ;;
  clean-results) cmd_clean_results ;;
  *)             sed -n '3,8p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
