# MyTimeline

X（旧 Twitter）風のタイムライン型 SNS アプリ。テキストと画像を中心とした短い投稿を時系列に表示し、ユーザー同士がコメント・いいね・フォローで交流できる。学習目的で要件定義から設計・実装までを進める個人開発プロジェクト。

複数ユーザーの利用を前提とし、いいね数・コメント数を可視化する。X との差別化として、**インプレッション数を表示せず、リツイート機能を持たない**ことを設計方針とする。

---

## 主な機能

- ユーザー認証（新規登録 / ログイン / ログアウト）
- タイムライン表示（「フォロー中」「すべて」の 2 タブ）
- 投稿（テキスト＋画像。画像は AWS S3 に保存）・自分の投稿の削除
- コメント（投稿へのコメント・件数表示・自分のコメント削除）
- いいね（いいね / 取り消し・件数表示。1 ユーザー 1 投稿 1 回）
- フォロー（フォロー / 解除・フォロー中 / フォロワー数表示）
- ユーザー検索（ユーザー名の部分一致検索、投稿・コメントからのプロフィール遷移）
- PostgreSQL によるデータ永続化

**対象外（X との差別化）:** インプレッション数表示 / リツイート / DM / 通知 / ハッシュタグ

---

## 技術スタック

| レイヤー | 主な技術 |
|---------|---------|
| フロントエンド | React 19 + TypeScript 6 + Vite 8 + Tailwind CSS 3 + TanStack Query |
| バックエンド | Java 25 + Spring Boot 4.0 + MyBatis + Flyway + Spring Security（JWT 認証） |
| データベース | PostgreSQL 15（ローカル）/ PostgreSQL 16（RDS, 本番想定） |
| 画像ストレージ | AWS S3 |
| ローカル実行 | Docker + Docker Compose |
| 本番インフラ（暫定・前提） | AWS（CloudFront + S3 + ALB + EC2 + RDS） |

詳細は [docs/02_tech_stack.md](docs/02_tech_stack.md) を参照。

---

## ディレクトリ構成（予定）

```
MyTimeline/
├── backend/                  # Spring Boot アプリケーション
│   ├── src/main/java/
│   │   └── com/example/mytimeline/
│   │       ├── config/       # Spring Security 設定など
│   │       ├── controller/   # REST コントローラー
│   │       ├── service/      # ビジネスロジック
│   │       ├── mapper/       # MyBatis Mapper（SQL）
│   │       ├── model/        # テーブルに対応するモデル（User/Post/PostImage/Comment/Like/Follow）
│   │       ├── security/     # 認証・認可（JWT）
│   │       ├── exception/    # 例外と共通エラーハンドリング
│   │       └── dto/          # リクエスト / レスポンス DTO
│   └── src/main/resources/
│       └── db/migration/     # Flyway マイグレーションスクリプト
├── frontend/                 # React アプリケーション（実装予定）
│   └── src/
│       ├── api/              # Axios クライアント設定
│       ├── components/       # UI コンポーネント（Timeline / PostCard / CommentList など）
│       ├── hooks/            # TanStack Query カスタムフック
│       └── types/            # API レスポンス型定義
├── docs/                     # 設計ドキュメント（要件定義・機能定義書）
├── docker-compose.yml        # PostgreSQL + Backend + Frontend（予定）
└── .claude/                  # Claude Code 用スキル・権限設定
```

---

## ローカル開発環境のセットアップ（予定）

> バックエンド / フロントエンドは実装予定。以下は想定手順。

### 前提条件

- Java 25
- Node.js 20+
- Docker Desktop
- AWS アカウント（S3 画像バケット用）

### 1. リポジトリをクローン

```bash
git clone <repository-url> MyTimeline
cd MyTimeline
```

### 2. 環境変数を設定

```bash
cp .env.example .env
# DB_NAME / DB_USER / DB_PASSWORD、S3 バケット名・認証情報などを設定
```

### 3. データベースを起動（Docker）

```bash
docker compose up -d db
```

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

---

## Docker Compose で全サービスを起動（予定）

```bash
docker compose up -d
```

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

## API エンドポイント（設計・機能定義書ベース）

> 未実装。各機能定義書（[docs/features/](docs/features/)）で定義した想定エンドポイント。

### 認証（[F01](docs/features/F01_auth.md)）

| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/auth/signup` | 新規登録 |
| POST | `/api/auth/login` | ログイン |
| POST | `/api/auth/logout` | ログアウト |
| GET | `/api/auth/me` | ログイン中ユーザー取得 |

### タイムライン（[F02](docs/features/F02_timeline.md)）

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/timeline/following` | フォロー中タイムライン |
| GET | `/api/timeline/all` | 全体タイムライン |

### 投稿・画像（[F03](docs/features/F03_post.md)）

| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/uploads/presign` | 画像アップロード用の署名付き URL 発行 |
| POST | `/api/posts` | 投稿作成 |
| GET | `/api/posts/{id}` | 投稿詳細取得 |
| DELETE | `/api/posts/{id}` | 投稿削除（本人のみ） |

### コメント（[F04](docs/features/F04_comment.md)）

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/posts/{postId}/comments` | コメント一覧取得 |
| POST | `/api/posts/{postId}/comments` | コメント作成 |
| DELETE | `/api/comments/{id}` | コメント削除（本人のみ） |

### いいね（[F05](docs/features/F05_like.md)）

| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/posts/{postId}/like` | いいね付与 |
| DELETE | `/api/posts/{postId}/like` | いいね取り消し |

### フォロー・ユーザー検索（[F06](docs/features/F06_follow.md)）

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/users/search` | ユーザー検索（部分一致） |
| GET | `/api/users/{username}` | プロフィール取得 |
| GET | `/api/users/{username}/posts` | ユーザーの投稿一覧 |
| POST | `/api/users/{userId}/follow` | フォロー |
| DELETE | `/api/users/{userId}/follow` | フォロー解除 |

---

## データモデル

6 テーブル構成（PostgreSQL）。詳細は [docs/07_er_diagram.md](docs/07_er_diagram.md)。

| テーブル | 説明 |
|---------|------|
| `users` | ユーザー（アカウント） |
| `posts` | 投稿（ポスト） |
| `post_images` | 投稿画像（S3 の `s3_key` を保持） |
| `comments` | 投稿へのコメント |
| `likes` | いいね（`(post_id, user_id)` は UNIQUE） |
| `follows` | フォロー関係（`(follower_id, followee_id)` は UNIQUE、自己フォロー禁止） |

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

要件定義のサマリは [requirements.md](requirements.md) を参照。
