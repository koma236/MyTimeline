# MyTimeline

X（旧 Twitter）風のタイムライン型 SNS アプリ。テキストと画像を中心とした短い投稿を時系列に表示し、ユーザー同士がコメント・いいね・フォローで交流できる。学習目的で要件定義から設計・実装までを進める個人開発プロジェクト。

複数ユーザーの利用を前提とし、いいね数・コメント数を可視化する。X との差別化として、**インプレッション数を表示せず、リツイート機能を持たない**ことを設計方針とする。

---

## 主な機能

| ID | 機能 | 状態 |
|----|------|------|
| F01 | ユーザー認証（新規登録 / ログイン / ログアウト） | **実装済み** |
| F02 | タイムライン表示（「フォロー中」「すべて」の 2 タブ・無限スクロール） | **実装済み** |
| F03 | 投稿（テキスト）・自分の投稿の編集 / 削除 | **実装済み**（画像添付は未実装） |
| F03 | 画像投稿（AWS S3 に保存） | 未実装 |
| F04 | コメント（投稿へのコメント・件数表示・自分のコメントの編集 / 削除） | **実装済み** |
| F05 | いいね（いいね / 取り消し・件数表示。1 ユーザー 1 投稿 1 回） | **実装済み** |
| F06 | フォロー（フォロー / 解除・フォロー中 / フォロワー数表示）・ユーザー検索 | **実装済み** |
| F07 | プロフィール表示・編集（表示名 / 自己紹介 / プロフィール画像） | **実装済み** |

**対象外（X との差別化）:** インプレッション数表示 / リツイート / DM / 通知 / ハッシュタグ

---

## 技術スタック

| レイヤー | 主な技術 |
|---------|---------|
| フロントエンド | React 19 + TypeScript 6 + Vite 8 + Tailwind CSS 3 + Axios（Lint: Oxlint / テスト: Vitest + React Testing Library） |
| バックエンド | Java 25 + Spring Boot 4.0 + MyBatis + Flyway + Spring Security（JWT 認証）（静的解析: Checkstyle + SpotBugs） |
| データベース | PostgreSQL 15（ローカル）/ PostgreSQL 16（RDS, 本番想定） |
| 画像ストレージ | AWS S3（ローカルは S3 互換の MinIO） |
| ローカル実行 | Docker + Docker Compose |
| 本番インフラ（暫定・前提） | AWS（CloudFront + S3 + ALB + EC2 + RDS） |

詳細は [docs/02_tech_stack.md](docs/02_tech_stack.md) を参照。

---

## ディレクトリ構成

```
MyTimeline/
├── backend/                  # Spring Boot アプリケーション
│   ├── config/
│   │   ├── checkstyle/       # Checkstyle 規約（命名・空白・波かっこ）
│   │   └── spotbugs/         # SpotBugs の除外設定（理由付き）
│   ├── src/main/java/
│   │   └── com/example/mytimeline/
│   │       ├── config/       # Spring Security / CORS 設定
│   │       ├── controller/   # REST コントローラー
│   │       ├── service/      # ビジネスロジック
│   │       ├── mapper/       # MyBatis Mapper（アノテーション SQL）
│   │       ├── model/        # テーブルに対応するモデル（User/Post/RefreshToken）
│   │       ├── security/     # 認証・認可（JWT・Cookie）
│   │       ├── storage/      # 画像ストレージ（S3 / MinIO）と画像検証
│   │       ├── exception/    # 例外と共通エラーハンドリング
│   │       └── dto/          # リクエスト / レスポンス DTO（record）
│   └── src/main/resources/
│       ├── db/migration/     # Flyway マイグレーションスクリプト
│       └── mapper/           # MyBatis XML Mapper（JOIN・動的条件を含む SQL）
├── frontend/                 # React アプリケーション
│   └── src/
│       ├── api/              # Axios クライアント設定（自動リフレッシュを含む）
│       ├── auth/             # 認証状態（Context）とルーティングガード
│       ├── components/       # UI コンポーネント（Field / Header / PostCard など）
│       ├── hooks/            # 画面横断のフック（useCursorPager とその利用側）
│       ├── pages/            # 画面（Login / Signup / Home / PostDetail / Profile）
│       ├── types/            # API レスポンス型定義（バックエンドの DTO と 1:1）
│       └── utils/            # 表示用の小さなユーティリティ（相対時刻など）
├── docs/                     # 設計ドキュメント（要件定義・機能定義書）
├── docker-compose.yml        # PostgreSQL + MinIO + Backend
└── .claude/                  # Claude Code 用スキル・権限設定
```

---

## ローカル開発環境のセットアップ

### 前提条件

- Java 25
- Node.js 20+
- Docker Desktop
- AWS アカウント（本番の S3 画像バケット用。**ローカル開発では不要** — S3 互換の MinIO を docker compose で立てる）

### 1. リポジトリをクローン

```bash
git clone <repository-url> MyTimeline
cd MyTimeline
```

### 2. 環境変数を設定

```bash
cp .env.example .env
# DB_NAME / DB_USER / DB_PASSWORD、S3_* などを設定。既定値のままローカル開発できる
```

### 3. データベースと画像ストレージを起動（Docker）

```bash
docker compose up -d db minio minio-init
```

`minio-init` はプロフィール画像用のバケットを作って終了する使い捨てコンテナ。
MinIO の管理コンソールは <http://localhost:9001>（既定のログインは `minioadmin` / `minioadmin`）。

> バックエンドをコンテナ外（`./gradlew bootRun`）で動かす場合は、`.env` の
> `S3_ENDPOINT` を `http://localhost:9000` に変えること。コンテナ名 `minio` は
> ホストから名前解決できないため。

### 4. バックエンドを起動

```bash
cd backend
./gradlew bootRun
# → http://localhost:8080
```

### 5. フロントエンドを起動

```bash
cd frontend
npm install
npm run dev
# → http://localhost:5173
```

> `.claude/skills/start-servers` スキルで、ポート競合を自動解消しつつ 8080 / 5173 で起動できる。

### 6. 品質チェック

コミット前に以下が通ることを確認する。

```bash
# バックエンド: コンパイル + Checkstyle（規約）+ SpotBugs（バグ検出）+ テスト
cd backend && ./gradlew build

# フロントエンド: Oxlint + 型チェック（tsc）+ テスト（Vitest）
cd frontend && npm run check
```

---

## Docker Compose でサービスを起動

```bash
docker compose up -d
```

> フロントエンドは compose に含めていない。`npm run dev`（5173）で起動し、
> Vite のプロキシ経由でバックエンド（8080）を同一オリジンとして参照する。

| サービス | URL |
|---------|-----|
| フロントエンド | http://localhost:5173 |
| バックエンド API | http://localhost:8080 |
| PostgreSQL | localhost:5432 |

ヘルスチェック:

```bash
curl -s http://localhost:8080/actuator/health
```

---

## API エンドポイント

> **実装済み**と記した行が現在動作するもの。それ以外は各機能定義書（[docs/features/](docs/features/)）で
> 定義した想定エンドポイントで、まだ実装していない。
>
> エラーはすべて共通形式 `{"message": "...", "fieldErrors": {"項目名": "..."}}` で返る
> （`fieldErrors` は項目に紐づくエラーがある場合のみ）。

### 認証（[F01](docs/features/F01_auth.md)）

| メソッド | パス | 説明 | 状態 |
|---------|------|------|------|
| POST | `/api/auth/signup` | 新規登録 | 実装済み |
| POST | `/api/auth/login` | ログイン | 実装済み |
| POST | `/api/auth/refresh` | アクセストークンの再発行（httpOnly Cookie で認証） | 実装済み |
| POST | `/api/auth/logout` | ログアウト（リフレッシュトークンを失効） | 実装済み |
| GET | `/api/auth/me` | ログイン中ユーザー取得 | 実装済み |

アクセストークン（`Authorization: Bearer`・15 分）とリフレッシュトークン（httpOnly Cookie・14 日・ローテーションあり）の 2 トークン方式。詳細は [F01](docs/features/F01_auth.md) を参照。

### タイムライン（[F02](docs/features/F02_timeline.md)）

| メソッド | パス | 説明 | 状態 |
|---------|------|------|------|
| GET | `/api/timeline/following` | フォロー中タイムライン（自分＋フォロー先。`?cursor=&limit=`。既定 20 件・最大 50 件） | 実装済み |
| GET | `/api/timeline/all` | 全体タイムライン（同上） | 実装済み |

ページングはカーソル方式。レスポンスの `nextCursor` を次のリクエストの `cursor` に渡す（`null` なら末尾）。

### 投稿・画像（[F03](docs/features/F03_post.md)）

| メソッド | パス | 説明 | 状態 |
|---------|------|------|------|
| POST | `/api/posts` | 投稿作成（本文 280 文字まで） | 実装済み |
| GET | `/api/posts/{id}` | 投稿詳細取得 | 実装済み |
| PUT | `/api/posts/{id}` | 投稿の本文を編集（本人のみ） | 実装済み |
| DELETE | `/api/posts/{id}` | 投稿削除（本人のみ） | 実装済み |
| POST | `/api/uploads/presign` | 画像アップロード用の署名付き URL 発行 | 未実装 |

### コメント（[F04](docs/features/F04_comment.md)）

| メソッド | パス | 説明 |
|---------|------|------|
| メソッド | パス | 説明 | 状態 |
|---------|------|------|------|
| GET | `/api/posts/{postId}/comments` | コメント一覧取得（`?cursor=&limit=`・古い順） | 実装済み |
| POST | `/api/posts/{postId}/comments` | コメント作成（本文 500 文字まで） | 実装済み |
| PUT | `/api/comments/{id}` | コメントの本文を編集（本人のみ） | 実装済み |
| DELETE | `/api/comments/{id}` | コメント削除（本人のみ） | 実装済み |

### いいね（[F05](docs/features/F05_like.md)）

| メソッド | パス | 説明 | 状態 |
|---------|------|------|------|
| POST | `/api/posts/{postId}/like` | いいね付与（冪等） | 実装済み |
| DELETE | `/api/posts/{postId}/like` | いいね取り消し（冪等） | 実装済み |

### プロフィール（[F07](docs/features/F07_profile.md)）

| メソッド | パス | 説明 | 状態 |
|---------|------|------|------|
| GET | `/api/users/{username}` | プロフィール取得（メールアドレスは含まない。フォロー中数・フォロワー数・フォロー状態を含む） | 実装済み |
| GET | `/api/users/{username}/posts` | ユーザーの投稿一覧（`?cursor=&limit=`） | 実装済み |
| PUT | `/api/users/me` | 表示名・自己紹介の更新 | 実装済み |
| PUT | `/api/users/me/avatar` | プロフィール画像のアップロード（multipart・JPEG / PNG・2MB まで） | 実装済み |
| DELETE | `/api/users/me/avatar` | プロフィール画像の削除 | 実装済み |

### フォロー・ユーザー検索（[F06](docs/features/F06_follow.md)）

| メソッド | パス | 説明 | 状態 |
|---------|------|------|------|
| GET | `/api/users/search` | ユーザー検索（username / 表示名の部分一致・`?q=&cursor=&limit=`） | 実装済み |
| POST | `/api/users/{userId}/follow` | フォロー（冪等） | 実装済み |
| DELETE | `/api/users/{userId}/follow` | フォロー解除（冪等） | 実装済み |

検索は大文字小文字を区別しない部分一致。`q` を省略・空にすると新着ユーザーが返る（フォローする相手を見つける導線のため）。自分自身のフォローは 400。

---

## データモデル

最終的に 7 テーブル構成（PostgreSQL）。詳細は [docs/07_er_diagram.md](docs/07_er_diagram.md)。
スキーマは Flyway（`backend/src/main/resources/db/migration/`）が唯一の情報源。

| テーブル | 説明 | 状態 |
|---------|------|------|
| `users` | ユーザー（アカウント。プロフィール画像は S3 の `avatar_key` を保持） | 作成済み（V1・V6 で `avatar_key` 追加） |
| `refresh_tokens` | リフレッシュトークン（1 行 = 1 セッション。SHA-256 ハッシュを保持） | 作成済み（V2） |
| `posts` | 投稿（ポスト） | 作成済み（V3） |
| `post_images` | 投稿画像（S3 の `s3_key` を保持） | 未作成 |
| `comments` | 投稿へのコメント | 作成済み（V4） |
| `likes` | いいね（`(post_id, user_id)` は UNIQUE） | 作成済み（V5） |
| `follows` | フォロー関係（`(follower_id, followee_id)` は UNIQUE、自己フォロー禁止） | 作成済み（V7） |

---

## 本番インフラ（暫定・前提）

AWS でのサーバ構築可否は未確定。構築する場合は以下を前提とする。

```
Browser → CloudFront ┬─ /*      → S3 (静的: React)
                     ├─ /api/*  → ALB → EC2 (Nginx → Spring Boot :8080) → RDS PostgreSQL
                     └─ 画像     → S3 (画像バケット)
```

- インフラ構成の全体像・各リソースの責務: [docs/09_infrastructure.md](docs/09_infrastructure.md)

---

## ドキュメント

### 要件定義

| ファイル | 内容 |
|---------|------|
| [docs/01_overview.md](docs/01_overview.md) | プロジェクト概要・スコープ・用語定義 |
| [docs/02_tech_stack.md](docs/02_tech_stack.md) | 技術スタック一覧（バージョン付き） |
| [docs/03_usecases.md](docs/03_usecases.md) | ユースケース定義（UC-01〜12） |
| [docs/04_features.md](docs/04_features.md) | 機能要件（機能一覧） |
| [docs/05_nonfunctional.md](docs/05_nonfunctional.md) | 非機能要件 |
| [docs/06_ui_design.md](docs/06_ui_design.md) | 画面設計（SCR-01〜06） |
| [docs/07_er_diagram.md](docs/07_er_diagram.md) | ER図・テーブル定義 |
| [docs/08_constraints.md](docs/08_constraints.md) | 制約・前提条件・未決事項 |
| [docs/09_infrastructure.md](docs/09_infrastructure.md) | インフラ構成（暫定・前提） |

### 機能定義書（機能単位の詳細）

| ファイル | 内容 |
|---------|------|
| [docs/features/F01_auth.md](docs/features/F01_auth.md) | 認証 |
| [docs/features/F02_timeline.md](docs/features/F02_timeline.md) | タイムライン |
| [docs/features/F03_post.md](docs/features/F03_post.md) | 投稿・画像投稿 |
| [docs/features/F04_comment.md](docs/features/F04_comment.md) | コメント |
| [docs/features/F05_like.md](docs/features/F05_like.md) | いいね |
| [docs/features/F06_follow.md](docs/features/F06_follow.md) | フォロー・ユーザー検索 |
| [docs/features/F07_profile.md](docs/features/F07_profile.md) | プロフィール表示・編集 |

要件定義のサマリは [requirements.md](requirements.md) を参照。
