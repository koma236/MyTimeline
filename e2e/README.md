# E2E テスト（Playwright）

ブラウザから実際の画面を操作して、ユースケース（docs/03）どおりに機能が繋がって動くことを確かめる。
あわせて **ブラウザ側の性能**（操作の反映時間・読み込み指標・長いスクロール後の資源）も同じ仕組みで計測する。
方針・テスト一覧・合否基準・ベースラインは [docs/14_e2e_test.md](../docs/14_e2e_test.md) を参照。

perf/ と同じく **任意のタイミングで手動実行する**。quality-check スキルの必須項目ではない。CI（`.github/workflows/e2e.yml`）では PR ごと・main への push ごとにシナリオだけを実行し、性能計測は手動のみ。テストコード自体の Lint / 型チェック（`npm run check`、Docker 不要）は `.github/workflows/quality-check.yml` の `e2e-static` ジョブが実行する。

## 必要なもの

- Docker（Docker Compose）と、リポジトリ直下の `.env`（通常の開発と同じもの）
- Node.js 20+（frontend の本番ビルドと Playwright の実行に使う）
- 初回のみ:

```bash
cd e2e && npm ci && npx playwright install chromium
```

## 使い方

リポジトリのルートで実行する。

```bash
bash e2e/run.sh up               # 1. e2e 用 DB・バケットを作り、backend を e2e 用設定で起動（初回は backend のビルドで数分）
bash e2e/run.sh run scenario     # 2. シナリオ・耐性・アクセシビリティ（約 30 秒）
bash e2e/run.sh run perf         # 3. ブラウザ性能の計測（約 30 秒。結果は e2e/results/ に出る）
bash e2e/run.sh down             # 4. backend を開発用に戻し、テストデータを削除
```

**`down` を忘れないこと。** `up` は backend コンテナを e2e 用 DB 向けに作り直すので、そのまま開発を続けると e2e 用のデータを見ることになる。

| コマンド | 内容 |
|---|---|
| `up [--no-build]` | DB `mytimeline_e2e` とバケット `mytimeline-images-e2e` を作成し、backend を現在のソースからビルドし直して起動。backend を変えていないなら `--no-build` で数分節約できる |
| `run scenario [引数]` | テーブルを空にしてから `tests/{scenario,robustness,a11y}/` を実行。引数はそのまま `playwright test` に渡る |
| `run perf [引数]` | テーブルを空にしてから `tests/perf/` を直列・1 worker で実行し、結果を `e2e/results/` に書く |
| `reset` | e2e 用 DB のテーブルとバケットを空にする（`run` が毎回自動で行う。`E2E_KEEP_DATA=1` で省略） |
| `down [--keep-data]` | backend を開発用に戻してから e2e 用 DB とバケットを削除。`--keep-data` で残す |
| `clean-results` | `e2e/results/` を空にする |

テストコード（`e2e/**/*.ts`）を変えたら `cd e2e && npm run check`（Oxlint → tsc）を通すこと。Docker も backend も要らない。

```bash
# 1 ファイル・1 ケースだけ流す（引数は playwright test にそのまま渡る）
bash e2e/run.sh run scenario tests/scenario/post.spec.ts -g "境界値"
# ブラウザを表示して目で追う / 失敗した箇所で止めて調べる
bash e2e/run.sh run scenario --headed
bash e2e/run.sh run scenario --debug
# 計測の繰り返し回数を増やす（既定 5）
E2E_PERF_RUNS=10 bash e2e/run.sh run perf
# 失敗したテストのトレース・スクリーンショット付きレポートを開く
cd e2e && npm run report
```

## 仕組み

| 項目 | 内容 | 理由 |
|---|---|---|
| フロントエンド | Playwright が `npm run build && vite preview`（ポート 4173）を自動で起動する。`/api` は preview のプロキシで 8080 へ | dev サーバーは非バンドル・非 minify で性能の数値に意味が無い。開発用の 5173 とは衝突しない |
| backend | `docker-compose.e2e.yml` を重ねて DB `mytimeline_e2e` / バケット `mytimeline-images-e2e` を向かせる | 開発データにテストユーザーと投稿を混ぜない。perf と同じ方式 |
| テストデータ | 各テストが API で一意なユーザー（`e2e_<実行>_w<worker>_<連番>`）と前提データを作る。`run` のたびに `TRUNCATE ... CASCADE` で全消し | テスト同士が独立し、並列に流せる。毎回同じ状態から始まる |
| ログイン | `fixtures/test.ts` の `loginAs` が `context.request` で `/api/auth/login` を呼び、Set-Cookie をブラウザに入れてから `/` を開く | アクセストークンはメモリ、リフレッシュトークンは httpOnly Cookie なので `storageState` では再現できない。画面からのログインは UC-02 のテストだけ |
| セレクタ | `getByRole` / `getByLabel` / 文言。`data-testid` は使わない | 利用者と支援技術が見るのと同じ手がかりで操作する。フロントのソースを変えずに済む |
| ブラウザ | chromium のみ | docs/05「ブラウザ対応: Google Chrome 最新版」 |

`vite preview` が既に 4173 で動いていればそれを使う（`reuseExistingServer`）。**フロントを直した後に古いビルドのまま流さないよう、自分で起動した preview は止めておくこと。**

### 全テスト共通の自動チェック

`fixtures/test.ts` の `browserErrors` が、テスト中にブラウザで起きた未捕捉の例外と `console.error` を集め、1 件でもあればそのテストを失敗させる。Chrome が 4xx / 5xx 応答のたびに出す `Failed to load resource` だけは除外する（誤パスワードや耐性テストで意図的に出るため）。

### 絶対に refresh を呼ばない

`fixtures/api-client.ts` は Bearer トークンだけで backend を叩き、`/api/auth/refresh` を呼ばない。リフレッシュトークンはローテーションし、使用済みトークンの再提示は盗用とみなして**そのユーザーの全セッションを失効**させる。ブラウザが持つ Cookie と食い違うと、テスト中の画面が突然ログアウトされる。

## ディレクトリ

```
e2e/
├── run.sh                      # up / run / reset / down / clean-results
├── docker-compose.e2e.yml      # backend を e2e 用 DB・バケットに向ける上書き
├── playwright.config.ts        # scenario 用（並列）。tests/perf は含まない
├── playwright.perf.config.ts   # perf 用（直列・1 worker・リトライ無し）
├── global-setup.ts             # backend が応答するかの確認
├── fixtures/
│   ├── test.ts                 # 拡張 test（api / newUser / loginAs / openAs / browserErrors）
│   ├── api-client.ts           # シードデータの投入（signup / post / comment / like / follow / profile）
│   ├── helpers.ts              # 画面要素の取り方（postCard / likeButton / composer など）
│   ├── perf.ts                 # 計測（measureInteraction / readPageLoad / readRuntimeMetrics）
│   ├── images.ts               # 1×1 px の PNG（文字列で持つ）
│   └── env.ts                  # 接続先と PERF_RUNS
├── reporters/perf-reporter.ts  # 計測値を results/ に JSON + Markdown で書く
├── tests/
│   ├── scenario/               # UC-01〜12（auth / post / timeline / comment / like / follow / profile）
│   ├── robustness/             # API 失敗・401 リフレッシュ・セッション切れ・接続不能
│   ├── a11y/                   # axe-core で 7 画面を検査
│   └── perf/                   # page-load / interaction / scroll-memory
└── results/                    # 計測結果（git 管理外）
```

## テストを足すとき

- `@playwright/test` ではなく `fixtures/test.ts` の `test` / `expect` を import する（自動チェックとログイン用の fixture が付く）
- 前提データは `api` fixture で作り、「確かめたい操作」だけを画面で行う
- `describe` / `test` 名に技法（同値分割 / 境界値 / デシジョンテーブル / 状態遷移）を書く（単体・結合・k6 と同じ決まり）
- 「すべて」タブは並列で走る他のテストの投稿も混ざる。件数を断定するときは「フォロー中」タブかプロフィールの投稿一覧で行う
- 性能計測は `tests/perf/` に置き、`measureRepeated` で繰り返して中央値で判定する。終了条件はブラウザ内で評価されるので外側の変数は `arg` で渡す

## うまくいかないとき

| 症状 | 原因と対処 |
|---|---|
| `run` が「backend が e2e 用 DB を向いていません」で止まる | 開発用 DB に書き込まないためのガード。`up` を先に実行する |
| `run` が「backend が応答しません」 | `docker ps` で `mytimeline_backend` を確認。`docker compose logs backend` |
| webServer の起動でタイムアウトする | `cd frontend && npm run build` が通るか確認する（型エラーがあると preview まで進まない） |
| 画像つき投稿・アバターのテストだけ落ちる | MinIO（`localhost:9000`）にブラウザから届いていない。`docker ps` で `mytimeline_minio` を確認 |
| 突然ログイン画面に戻される | どこかで refresh を二重に呼んでいる（上記「絶対に refresh を呼ばない」） |
| perf の数値が前回と大きく違う | 他のプロセス（IDE のインデックス・Docker のビルド）が動いていないか確認し、`E2E_PERF_RUNS` を増やして取り直す |
