# MyTimeline フロントエンド

React + TypeScript + Vite + Tailwind CSS で構築した SPA。
現時点で認証（F01）・タイムライン（F02）・投稿（F03、画像を除く）の画面を実装済み。

## 起動

```bash
npm install
npm run dev     # → http://localhost:5173
```

バックエンド（`http://localhost:8080`）が起動している必要がある。リポジトリルートで `docker compose up db backend -d` を実行しておくこと。

> ポートは 5173 固定（`strictPort: true`）。`.claude/skills/start-servers` が 8080 / 5173 を前提にしているため変更しないこと。

## 開発時の注意

**`/api` は Vite のプロキシで 8080 へ転送している**（`vite.config.ts`）。プロキシを外して `http://localhost:8080` を直接叩く構成にすると、リフレッシュトークンの Cookie が `SameSite=Lax` のため送信されずログイン状態を維持できなくなる。本番も CloudFront が `/api/*` をバックエンドへ流す同一オリジン構成なので、この形が本番と一致する。

## 認証の仕組み

詳細は [docs/features/F01_auth.md](../docs/features/F01_auth.md) を参照。フロント側の要点は以下の 3 つ。

- **アクセストークンはメモリのみに保持する**（`src/api/client.ts`）。localStorage に置くと XSS で読み出せてしまうため。リロードで消えるが、起動時に `POST /api/auth/refresh` を呼んで復元する
- **401 を受けたら自動でリフレッシュして元のリクエストを 1 度だけ再送する**。ログイン・新規登録・リフレッシュ自身への 401 は対象外（そのままフォームにエラーを表示する）
- **並行する 401 は 1 本のリフレッシュにまとめる**。バックエンドはリフレッシュトークンをローテーションし、使用済みトークンの再提示を盗用とみなして全セッションを失効させるため、同時に複数投げると正規の利用者が強制ログアウトされる

**入力チェックはフロント側に持たない。** バックエンドの Bean Validation が返す `fieldErrors` をそのまま各項目に表示する（ルールとメッセージの二重管理を避けるため）。

## ディレクトリ

```
src/
├── api/          Axios クライアント（自動リフレッシュ）と認証 / 投稿 API
├── auth/         認証状態の Context とルーティングガード
├── components/   共通 UI（Field / FormError / SubmitButton / Header / PostCard / PostComposer）
├── hooks/        画面横断のフック（useTimeline: 取得とカーソルページング）
├── pages/        画面（Login / Signup / Home / PostDetail）
├── types/        API の型定義（バックエンドの DTO と 1:1）
└── utils/        表示用のユーティリティ（相対時刻）
```

## スクリプト

| コマンド | 内容 |
|---------|------|
| `npm run dev` | 開発サーバー（5173） |
| `npm run build` | 型チェック（`tsc -b`）＋ 本番ビルド |
| `npm run typecheck` | 型チェックのみ（`tsc -b`） |
| `npm run lint` | Oxlint（警告も 0 件であること） |
| `npm run test` | Vitest（1 回実行） |
| `npm run test:watch` | Vitest（監視モード） |
| `npm run check` | `lint` ＋ `typecheck` ＋ `test`。**コミット前にこれを通すこと** |
| `npm run preview` | ビルド成果物の確認 |

## 品質ゲート

- **TypeScript は `strict` ＋ `noUncheckedIndexedAccess`**（`tsconfig.app.json`）。
  配列や `Record` への添字アクセスは `undefined` を含むものとして扱われるので、
  `?.` や `??` で必ず受けること
- **Oxlint は `correctness` / `suspicious` / `perf` を有効**にし、
  `react/exhaustive-deps`（hooks の依存配列漏れ）と `react/rules-of-hooks` をエラーにしている
- 無効化しているルールには `.oxlintrc.json` に理由を書いてある。
  無効化を増やす場合も必ず理由を添えること

## テスト

Vitest + React Testing Library（`environment: 'jsdom'`、設定は `vite.config.ts` の `test`）。
テストは対象と同じ場所に `*.test.ts` / `*.test.tsx` で置く。共通の前準備は `src/test/setup.ts`
（jest-dom のマッチャ登録と、テスト間の DOM 片付け）。

```bash
npm run test          # 1 回実行
npm run test:watch    # 監視モード
```

**壊れても画面上は気付きにくい箇所**を優先して押さえている。

| 対象 | 主に守っているもの |
|------|------------------|
| `src/hooks/useTimeline.ts` | カーソルページング、タブ切替時の取り直し、**追い越されたレスポンスの破棄** |
| `src/api/client.ts` | 401 の自動リフレッシュと **1 度だけの再送**、**並行する 401 が 1 本の refresh にまとまること** |
| `src/api/client.ts`（`toApiError`） | ネットワークエラー / `fieldErrors` 付き / 想定外の分岐 |
| `src/auth/RouteGuards.tsx` | `loading` / `authenticated` / `anonymous` の出し分け |

`client.ts` のテストは HTTP を出さず、`apiClient.defaults.adapter` を差し替えて応答を組み立てる。
`accessToken` と `refreshPromise` はモジュールレベルの状態なので、テストごとに
`vi.resetModules()` してから読み直すこと（前のテストの状態が漏れると結果が変わる）。
