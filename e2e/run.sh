#!/usr/bin/env bash
# E2E テスト（Playwright）の実行スクリプト。perf/run.sh と同じ構成。
#
#   bash e2e/run.sh up [--no-build]        e2e 用 DB・バケットを作り、backend を e2e 用設定で起動
#   bash e2e/run.sh run <scenario|perf> [playwright の引数...]
#                                          テーブルを空にしてから実行（E2E_KEEP_DATA=1 で空にしない）
#   bash e2e/run.sh reset                  e2e 用 DB のテーブルとバケットを空にする
#   bash e2e/run.sh down [--keep-data]     backend を開発用に戻し、e2e 用 DB とバケットを削除
#   bash e2e/run.sh clean-results          e2e/results/ の計測結果を削除
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# 削除対象の名前はここに固定で持つ。.env から組み立てると、設定ミスで開発用を消しかねない。
# 変えるときは e2e/docker-compose.e2e.yml も合わせること
readonly E2E_DB="mytimeline_e2e"
readonly E2E_BUCKET="mytimeline-images-e2e"
readonly BACKEND_CONTAINER="mytimeline_backend"
readonly DB_CONTAINER="mytimeline_db"
readonly MINIO_CONTAINER="mytimeline_minio"
readonly BASE_URL="http://localhost:8080"

DC_DEV=(docker compose)
DC_E2E=(docker compose -f docker-compose.yml -f e2e/docker-compose.e2e.yml)

log()  { printf '\033[1;34m[e2e]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[e2e]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[e2e]\033[0m %s\n' "$*" >&2; exit 1; }

# .env を source しない。JWT_SECRET などに記号が入っているとシェルが解釈して壊れるため
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
  [[ "$DEV_DB" != "$E2E_DB" ]] || die ".env の DB_NAME が e2e 用 DB 名（${E2E_DB}）と同じです。中断します"
  [[ "$DEV_BUCKET" != "$E2E_BUCKET" ]] || die ".env の S3_BUCKET が e2e 用バケット名（${E2E_BUCKET}）と同じです。中断します"
}

psql_dev() { docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DEV_DB" "$@"; }
psql_e2e() { docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$E2E_DB" "$@"; }

minio_mc() {
  docker exec "$MINIO_CONTAINER" sh -c \
    'mc alias set e2e http://localhost:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc "$@"' mc "$@"
}

e2e_db_exists() {
  [[ "$(psql_dev -tAc "SELECT 1 FROM pg_database WHERE datname = '$E2E_DB'")" == "1" ]]
}

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
  local build=(--build)
  [[ "${1:-}" == "--no-build" ]] && build=()
  guard_names
  log "db と minio を起動します"
  "${DC_DEV[@]}" up -d --wait db minio

  if e2e_db_exists; then
    log "e2e 用 DB（${E2E_DB}）は既にあります"
  else
    log "e2e 用 DB（${E2E_DB}）を作成します"
    psql_dev -c "CREATE DATABASE $E2E_DB" >/dev/null
  fi

  log "e2e 用バケット（${E2E_BUCKET}）を用意します"
  minio_mc mb --ignore-existing "e2e/$E2E_BUCKET" >/dev/null

  # --build: 試すのは「いまチェックアウトしているコード」でなければ意味がない。
  # backend を変えていないと分かっているときだけ --no-build で時間を節約する
  log "backend を e2e 用設定で起動します（テーブルは Flyway が作ります）"
  "${DC_E2E[@]}" up -d ${build[@]+"${build[@]}"} backend
  wait_ready

  [[ "$(backend_db_name)" == "$E2E_DB" ]] || die "backend が $E2E_DB を向いていません"
  log "準備完了。次は bash e2e/run.sh run scenario"
  warn "backend は e2e 用 DB を向いています。開発に戻る前に必ず bash e2e/run.sh down を実行してください"
}

cmd_reset() {
  guard_names
  e2e_db_exists || die "e2e 用 DB がありません。先に bash e2e/run.sh up を実行してください"
  # users を起点に外部キーで繋がる全テーブル（posts / comments / likes / follows / refresh_tokens /
  # post_images）が CASCADE で空になる。id も 1 から振り直す
  psql_e2e -c "TRUNCATE TABLE users RESTART IDENTITY CASCADE" >/dev/null
  minio_mc rm --recursive --force "e2e/$E2E_BUCKET" >/dev/null 2>&1 || true
  log "e2e 用 DB とバケットを空にしました"
}

cmd_run() {
  local kind="${1:-}"
  local config
  case "$kind" in
    scenario) config="playwright.config.ts" ;;
    perf)     config="playwright.perf.config.ts" ;;
    *) die "種別を scenario か perf で指定してください。例: bash e2e/run.sh run scenario" ;;
  esac
  shift
  guard_names
  [[ -d e2e/node_modules ]] || die "e2e/node_modules がありません。cd e2e && npm ci && npx playwright install chromium を実行してください"

  # 開発用 DB を向いた backend に対して流すと、開発データにテストユーザーと投稿が混ざる
  local current
  current="$(backend_db_name)"
  [[ "$current" == "$E2E_DB" ]] \
    || die "backend が e2e 用 DB を向いていません（現在: ${current:-未起動}）。先に bash e2e/run.sh up を実行してください"
  curl -fs "$BASE_URL/actuator/health/readiness" >/dev/null || die "backend が応答しません"

  if [[ "${E2E_KEEP_DATA:-}" == "1" ]]; then
    warn "E2E_KEEP_DATA=1: テーブルを空にせずに実行します"
  else
    cmd_reset
  fi

  log "Playwright を実行します（${config}）"
  local code=0
  (cd e2e && npx playwright test -c "$config" "$@") || code=$?

  if [[ $code -eq 0 ]]; then
    log "合格（全テストが通りました）"
  else
    warn "失敗（終了コード: ${code}）。cd e2e && npm run report でレポートを開けます"
  fi
  warn "終わったら bash e2e/run.sh down で開発用の状態に戻してください"
  return "$code"
}

cmd_down() {
  local keep=0
  [[ "${1:-}" == "--keep-data" ]] && keep=1
  guard_names

  # 順序が大事: 先に backend を開発用に戻して e2e 用 DB への接続を切ってから DROP する
  log "backend を開発用設定で作り直します"
  "${DC_DEV[@]}" up -d backend
  wait_ready
  [[ "$(backend_db_name)" == "$DEV_DB" ]] || die "backend が開発用 DB（${DEV_DB}）に戻っていません"

  if [[ $keep -eq 1 ]]; then
    log "--keep-data: e2e 用 DB とバケットは残しました"
    return 0
  fi

  log "e2e 用 DB（${E2E_DB}）を削除します"
  psql_dev -c "DROP DATABASE IF EXISTS $E2E_DB WITH (FORCE)" >/dev/null

  log "e2e 用バケット（${E2E_BUCKET}）を削除します"
  minio_mc rb --force "e2e/$E2E_BUCKET" >/dev/null 2>&1 || log "バケットは既にありません"

  log "後片付け完了。開発用 DB（${DEV_DB}）とバケット（${DEV_BUCKET}）には触れていません"
}

cmd_clean_results() {
  find e2e/results -mindepth 1 ! -name .gitkeep -delete
  log "e2e/results/ を空にしました"
}

case "${1:-}" in
  up)            shift; cmd_up "$@" ;;
  reset)         cmd_reset ;;
  run)           shift; cmd_run "$@" ;;
  down)          shift; cmd_down "$@" ;;
  clean-results) cmd_clean_results ;;
  *)             sed -n '3,9p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
