# パフォーマンステスト（k6）

バックエンド API に負荷をかけて応答時間を測る。**任意のタイミングで手動実行する**もので、CI でも quality-check スキルでも動かない。
方針・テスト種別・合否基準の根拠は [docs/13_performance_test.md](../docs/13_performance_test.md) を参照。

## 必要なもの

- Docker（Docker Compose）。k6 は Docker イメージで動かすのでインストール不要
- リポジトリ直下の `.env`（通常の開発と同じもの）

## 使い方

リポジトリのルートで実行する。

```bash
bash perf/run.sh up          # 1. perf 用 DB・バケットを作り、backend を perf 用設定で起動
bash perf/run.sh seed        # 2. テストデータを投入（約 5 秒）
bash perf/run.sh run smoke   # 3. 計測。実行中は http://localhost:5665 でライブ表示
bash perf/run.sh down        # 4. backend を開発用に戻し、テストデータを削除
```

**`down` を忘れないこと。** `up` は backend コンテナを perf 用 DB 向けに作り直すので、そのまま開発を続けると perf 用のデータを見ることになる。

| コマンド | 内容 |
|---|---|
| `up` | DB `mytimeline_perf` とバケット `mytimeline-images-perf` を作成。backend を現在のソースからビルドし直して起動（テーブルは Flyway が作る） |
| `seed` | `perf/seed/seed.sql` を投入。先に全消しするので、何度流しても同じ状態になる |
| `run <種別>` | `perf/k6/tests/<種別>.js` を実行。閾値違反なら非ゼロで終了する（k6 の終了コード 99） |
| `down` | backend を開発用に戻してから、perf 用 DB とバケットを**削除** |
| `down --keep-data` | backend だけ戻し、perf 用 DB とバケットは残す（続けて計測したいとき） |
| `clean-results` | `perf/results/` のレポートを削除 |

## テスト種別

`bash perf/run.sh run <種別>` の種別は `perf/k6/tests/` のファイル名。目的と合否基準の詳細は [docs/13_performance_test.md](../docs/13_performance_test.md) の 15.6。

| 種別 | 所要時間 | 何が分かるか | 合否 |
|---|---|---|---|
| `smoke` | 1 分 | 環境とスクリプトが壊れていないか。他の種別の前に必ず流す | エラー 0 件 |
| `load` | 13 分 | 通常負荷（50 VU）での応答時間。前回と比較する基準 | p95 < 1 秒、失敗率 < 1 % |
| `stress` | 15 分 | 200 → 400 → 800 → 1600 VU と上げ、どこで限界を迎えるか | 失敗率 5 % 超で自動中断（合否は付けない） |
| `spike` | 5 分 | 10 → 300 VU の急増と、収まった後に性能が戻るか | 急増後の区間が p95 < 1 秒 |
| `soak` | 60 分 | 長時間でのヒープ増加・接続リーク・トークンの取り直し | load と同じ |
| `focus` | 5 分 | 既知の懸念（TBD-09 / 12 / 13、ログイン）を条件違いの対で比較 | 記録のみ |

VU 数や時間は環境変数で一時的に変えられる。`PERF_` で始まる変数はすべて k6 に渡る。

| 種別 | 変数（既定値） |
|---|---|
| `load` | `PERF_LOAD_VUS`（50）、`PERF_LOAD_RAMP`（2m）、`PERF_LOAD_HOLD`（10m） |
| `stress` | `PERF_STRESS_STEPS`（200,400,800,1600）、`PERF_STRESS_HOLD`（3m） |
| `spike` | `PERF_SPIKE_BASE_VUS`（10）、`PERF_SPIKE_PEAK_VUS`（300） |
| `soak` | `PERF_SOAK_VUS`（30）、`PERF_SOAK_DURATION`（60m） |
| `focus` | `PERF_FOCUS_RATE`（20 req/s）、`PERF_FOCUS_LOGIN_RATE`（5 req/s）、`PERF_FOCUS_DURATION_S`（40） |

```bash
# スクリプトを直した後の動作確認（1 分半で終わる load）
PERF_LOAD_VUS=10 PERF_LOAD_RAMP=20s PERF_LOAD_HOLD=40s bash perf/run.sh run load
# アクセストークンの 15 分境界だけ確かめる soak
PERF_SOAK_VUS=2 PERF_SOAK_DURATION=16m bash perf/run.sh run soak
```

既定値を変えて測った数値は、既定値での結果と比較できない。docs/13 に記録するのは既定値での結果だけにする。

### 集計結果の読み方

- `http_req_duration{type:read}` / `{type:write}` … 合否の対象。`type:auth`（ログイン）と `type:upload`（画像つき投稿）は別枠
- `ep_GET_api_timeline_all` など `ep_` で始まる行 … エンドポイント別の応答時間。どこが遅いかはここで見る
- `perf_login_initial` / `perf_login_token_age` / `perf_login_after_401` … ログインした理由の内訳。soak では `token_age` が 1 以上、`after_401` が 0 なら、期限切れ前に正しく取り直せている
- `dropped_iterations` … 到着率で流すシナリオ（load の画像投稿、focus）で、VU が足りず捨てられた反復。0 でなければ「その到着率では捌けなかった」と読む

### 画像つき投稿の画像

既定では 1×1 px の PNG（約 70 バイト）を送る。測れるのは「形式検証 + S3 への保存の往復」で、転送量の影響は含まない。実サイズに近い画像で測るときは `perf/k6/assets/upload.jpg`（2 MB 以下）を置く。このフォルダは git 管理外。

## テストデータ

開発用とは別の入れ物にだけ作るので、開発データには触れない。`down` で入れ物ごと消える。

| | 開発用 | perf 用 |
|---|---|---|
| データベース | `.env` の `DB_NAME` | `mytimeline_perf` |
| バケット | `.env` の `S3_BUCKET`（既定 `mytimeline-images`） | `mytimeline-images-perf` |

既定の件数は users 1,000 / posts 100,000 / follows 約 52,000 / likes 約 300,000 / comments 200,000 / post_images 50,000。環境変数で変えられる。

```bash
PERF_USERS=5000 PERF_POSTS=500000 bash perf/run.sh seed
PERF_USERS=5000 PERF_POSTS=500000 bash perf/run.sh run smoke   # run にも同じ値を渡す
```

全ユーザーのパスワードは `PerfTest1234`、ユーザー名は `perf_user_00001` 〜。`perf_user_00042` の id は 42 になる。

**比較できる数値を取りたいときは、計測のたびに `seed` を流し直す。** 書き込み系のシナリオがデータを増やすため、流し直さないと回を追うごとに条件が変わる。

## 結果の見方

`perf/results/` に実行ごとのファイルができる（git 管理外）。

| ファイル | 内容 |
|---|---|
| `<日時>-<種別>-report.html` | k6 のダッシュボード。ブラウザで開く |
| `<日時>-<種別>-summary.json` | 集計値。前回との比較や転記に使う。`thresholds` の値は「違反したか」を表すので、`false` が合格 |
| `<日時>-<種別>-docker-stats.csv` | backend / db コンテナの CPU・メモリ（5 秒間隔） |
| `<日時>-<種別>-prometheus-{before,after}.txt` | 実行前後の `/actuator/prometheus`。`hikaricp_connections_acquire_seconds_sum`（DB 接続の取得待ちの累計。before と after の差を見る）、`jvm_memory_used_bytes`、`jvm_gc_pause_seconds`、`http_server_requests_seconds_bucket` を見る |

k6 の数値で「遅い」ことが分かり、docker-stats と prometheus で「なぜ遅いか」（CPU か、DB 接続待ちか、GC か）を切り分ける。

## シナリオを足すとき

- エンドポイントの呼び出しは `k6/lib/api.js` に足し、必ず `type`（read / write / auth / upload）と `name`（パスのひな形）のタグを付ける。`name` が無いと id や cursor ごとに系列が分かれて集計できない
- ログインは `k6/lib/auth.js` の `ensureSession()` に任せる。**通常のシナリオで refresh を呼ばない**こと。リフレッシュトークンは使い回すと盗用扱いになり、そのユーザーの全セッションが失効する
- `group` 名にはテスト設計技法（同値分割 / 境界値 / 状態遷移）を書く

## うまくいかないとき

| 症状 | 原因と対処 |
|---|---|
| `run` が「backend が perf 用 DB を向いていません」で止まる | 開発用 DB に負荷をかけないためのガード。`up` を先に実行する |
| smoke のログインが 401 | `seed` を流していない |
| `docker ps` で backend が `unhealthy` | 既存の問題。実行イメージに `curl` が無く compose のヘルスチェックが常に失敗する。アプリは動いているので計測には影響しない |
| ポート 5665 が使用中 | 前回の k6 コンテナが残っている。`docker ps` で確認して停止する |
