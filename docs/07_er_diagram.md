# 8. ER図

### 8.1 テーブル一覧

| テーブル名 | 説明 |
|-----------|------|
| users | ユーザー（アカウント）を管理する |
| refresh_tokens | ログインセッション（リフレッシュトークン）を管理する |
| posts | 投稿（ポスト）を管理する |
| post_images | 投稿に添付された画像（S3キー）を管理する |
| comments | 投稿へのコメントを管理する |
| likes | 投稿へのいいねを管理する |
| follows | ユーザー間のフォロー関係を管理する |

### 8.2 ER図

```
┌─────────────────────────────┐
│           users             │
├─────────────────────────────┤
│ PK  id            BIGINT    │
│     username      VARCHAR U │
│     display_name  VARCHAR   │
│     email         VARCHAR U │
│     password_hash VARCHAR   │
│     bio           VARCHAR   │
│     avatar_key    VARCHAR   │
│     created_at    DATETIME  │
│     updated_at    DATETIME  │
└──────┬────────┬────────┬────┘
       │        │        │
       │        │        │        ┌──────────────────────────┐
       │        │        └──────< │         follows          │
       │        │                 ├──────────────────────────┤
       │        │                 │ PK  id           BIGINT  │
       │        │        (followee)│ FK  follower_id  BIGINT  │
       │        └───────────────< │ FK  followee_id  BIGINT  │
       │                          │     created_at   DATETIME│
       │                          └──────────────────────────┘
       │
       │        ┌──────────────────────────┐
       ├──────< │          posts           │
       │        ├──────────────────────────┤       ┌──────────────────────────┐
       │        │ PK  id          BIGINT   │──┐    │       post_images        │
       │        │ FK  user_id     BIGINT   │  └──< ├──────────────────────────┤
       │        │     body        TEXT     │       │ PK  id          BIGINT   │
       │        │     created_at  DATETIME │       │ FK  post_id     BIGINT   │
       │        │     updated_at  DATETIME │       │     s3_key      VARCHAR  │
       │        └───────┬──────────┬───────┘       │     position    INTEGER  │
       │                │          │               │     created_at  DATETIME │
       │                │          │               └──────────────────────────┘
       │      ┌─────────▼──┐    ┌──▼─────────┐
       │      │  comments  │    │   likes    │
       │      ├────────────┤    ├────────────┤
       ├────< │ PK id      │    │ PK id      │ >──┤ (user_id は users を参照)
       │      │ FK post_id │    │ FK post_id │
       └────< │ FK user_id │    │ FK user_id │ >──┘
              │ body       │    │ created_at │
              │ created_at │    │ UQ(post_id,│
              │ updated_at │    │    user_id)│
              └────────────┘    └────────────┘
   （U = UNIQUE, UQ = 複合UNIQUE）
```

### 8.3 テーブル定義

#### users テーブル

| カラム名 | 型 | NOT NULL | 説明 |
|---------|-----|----------|------|
| id | BIGINT | ○ | 主キー（自動採番） |
| username | VARCHAR(50) | ○ | ユーザー識別 ID。**UNIQUE**（例: `taro`） |
| display_name | VARCHAR(100) | ○ | 画面表示名（重複可） |
| email | VARCHAR(255) | ○ | メールアドレス。**UNIQUE** |
| password_hash | VARCHAR(255) | ○ | ハッシュ化済みパスワード（平文保存しない） |
| bio | VARCHAR(300) | - | 自己紹介。未設定は NULL |
| avatar_key | VARCHAR(512) | - | プロフィール画像の S3 キー。画像本体は S3（ローカルは MinIO）に置き、DB はキーのみ持つ。未設定は NULL（[features/F07_profile.md](features/F07_profile.md)） |
| created_at | DATETIME | ○ | 作成日時 |
| updated_at | DATETIME | ○ | 更新日時 |

#### refresh_tokens テーブル

1 行が 1 セッション（1 ブラウザ）に対応する。詳細は [features/F01_auth.md](features/F01_auth.md) 2. 認証方式を参照。

| カラム名 | 型 | NOT NULL | 説明 |
|---------|-----|----------|------|
| id | BIGINT | ○ | 主キー（自動採番） |
| user_id | BIGINT | ○ | 外部キー（users.id）。ユーザー削除時は CASCADE |
| token_hash | CHAR(64) | ○ | トークン生値の SHA-256（16進64文字）。**UNIQUE**。生値は保存しない |
| expires_at | DATETIME | ○ | 有効期限（既定 14 日） |
| revoked_at | DATETIME | - | 失効日時。NULL なら有効。ローテーション・ログアウト・盗用検知で設定 |
| created_at | DATETIME | ○ | 作成日時 |

#### posts テーブル

| カラム名 | 型 | NOT NULL | 説明 |
|---------|-----|----------|------|
| id | BIGINT | ○ | 主キー（自動採番） |
| user_id | BIGINT | ○ | 外部キー（users.id）。投稿者 |
| body | TEXT | ○ | 投稿本文（上限文字数はアプリ側で制御。例: 280 文字） |
| created_at | DATETIME | ○ | 作成日時 |
| updated_at | DATETIME | ○ | 更新日時 |

#### post_images テーブル

| カラム名 | 型 | NOT NULL | 説明 |
|---------|-----|----------|------|
| id | BIGINT | ○ | 主キー（自動採番） |
| post_id | BIGINT | ○ | 外部キー（posts.id） |
| s3_key | VARCHAR(512) | ○ | S3 オブジェクトキー。画像本体は S3 に保存 |
| position | INTEGER | ○ | 投稿内での表示順（0〜3） |
| created_at | DATETIME | ○ | 作成日時 |

#### comments テーブル

| カラム名 | 型 | NOT NULL | 説明 |
|---------|-----|----------|------|
| id | BIGINT | ○ | 主キー（自動採番） |
| post_id | BIGINT | ○ | 外部キー（posts.id）。対象の投稿 |
| user_id | BIGINT | ○ | 外部キー（users.id）。コメント投稿者 |
| body | VARCHAR(500) | ○ | コメント本文 |
| created_at | DATETIME | ○ | 作成日時 |
| updated_at | DATETIME | ○ | 更新日時 |

#### likes テーブル

| カラム名 | 型 | NOT NULL | 説明 |
|---------|-----|----------|------|
| id | BIGINT | ○ | 主キー（自動採番） |
| post_id | BIGINT | ○ | 外部キー（posts.id）。いいね対象の投稿 |
| user_id | BIGINT | ○ | 外部キー（users.id）。いいねしたユーザー |
| created_at | DATETIME | ○ | 作成日時 |

#### follows テーブル

| カラム名 | 型 | NOT NULL | 説明 |
|---------|-----|----------|------|
| id | BIGINT | ○ | 主キー（自動採番） |
| follower_id | BIGINT | ○ | 外部キー（users.id）。フォローする側 |
| followee_id | BIGINT | ○ | 外部キー（users.id）。フォローされる側 |
| created_at | DATETIME | ○ | 作成日時 |

### 8.4 制約

- **外部キー制約:**
  - `refresh_tokens.user_id` → `users.id`
  - `posts.user_id` → `users.id`
  - `post_images.post_id` → `posts.id`
  - `comments.post_id` → `posts.id` / `comments.user_id` → `users.id`
  - `likes.post_id` → `posts.id` / `likes.user_id` → `users.id`
  - `follows.follower_id` → `users.id` / `follows.followee_id` → `users.id`
- **UNIQUE 制約:**
  - `users.username`、`users.email` は一意
  - `refresh_tokens.token_hash` は一意
  - `likes(post_id, user_id)` は複合 UNIQUE（同一ユーザーの二重いいねを防止）
  - `follows(follower_id, followee_id)` は複合 UNIQUE（二重フォローを防止）
  - `post_images(post_id, position)` は複合 UNIQUE（同じ投稿の同じ表示順に 2 枚入らない）
- **チェック制約:**
  - `follows.follower_id <> followee_id`（自分自身はフォロー不可）
  - `post_images.position` は 0〜3（最大 4 枚。F03 6.）
- **カスケード削除:**
  - 投稿削除時、配下の `post_images` / `comments` / `likes` を削除する
  - ユーザー削除時、配下の `refresh_tokens` を削除する
  - ユーザー削除時、配下の `posts`（およびその配下）・`comments` / `likes` / `follows`（follower・followee 両方向）を削除する
- **集計方針:**
  - いいね数・コメント数・フォロー中数・フォロワー数は、対応テーブルの `COUNT` で取得する（初期フェーズでは非正規化カウンタを持たない）
- **画像の扱い:**
  - 画像本体は AWS S3 に保存し、DB には `post_images.s3_key`・`users.avatar_key` のみ保持する
  - ローカル開発では S3 互換の MinIO を使う（docker-compose.yml）。アプリのコードは同じで、接続先の設定だけが変わる
  - プロフィール画像のキーは `avatars/{userId}/{UUID}.{拡張子}`。ファイル名にユーザーの入力を使わず、更新のたびに採番し直す（[features/F07_profile.md](features/F07_profile.md) 5.）
  - 投稿画像のキーは `posts/{userId}/{UUID}.{拡張子}`（[features/F03_post.md](features/F03_post.md) 5.）。差し替えは無く、投稿削除時にアプリが S3 からも削除する
  - 画面に出す URL は期限付きの署名付き URL を都度発行する。バケットは公開しない
