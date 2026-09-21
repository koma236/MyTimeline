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
| `<日時>-<種別>-prometheus-{before,after}.txt` | 実行前後の `/actuator/prometheus`。`hikaricp_connections_pending`、`jvm_memory_used_bytes`、`jvm_gc_pause_seconds`、`http_server_requests_seconds_bucket` を見る |

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
