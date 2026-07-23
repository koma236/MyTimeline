# 機能定義書 F06: フォロー・ユーザー検索

**関連:** 要件 [04_features.md](../04_features.md) 5.7 / ユースケース [03_usecases.md](../03_usecases.md) UC-10〜12 / 画面 [06_ui_design.md](../06_ui_design.md) SCR-05・SCR-06 / データ [07_er_diagram.md](../07_er_diagram.md) `follows`・`users`

## 1. 概要 / 目的
ユーザー同士のフォロー関係を管理し、フォロー中タイムライン（F02）の対象を決定する。フォローする相手を見つけるために、ユーザー検索と、投稿・コメントからのプロフィール遷移の 2 つの導線を提供する。

## 2. 機能詳細
- **フォロー / 解除:** プロフィール（SCR-05）または検索結果（SCR-06）のフォローボタンでトグルする
  - 自分自身はフォロー不可
  - 二重フォローは `follows(follower_id, followee_id)` の UNIQUE 制約で防止
- **フォロー/フォロワー数表示:** プロフィールに表示（`follows` の集計）
- **ユーザー検索:** 検索バーで username / 表示名を**部分一致**検索し、結果一覧を表示。未入力時は発見用に全ユーザー/新着ユーザーを表示
- **プロフィール遷移導線:** 投稿・コメント・検索結果に表示されるユーザー名はすべてプロフィール（SCR-05）へのリンクとし、そこからフォローできる

## 3. 対象画面
- SCR-05 プロフィール画面（情報・フォロー/フォロワー数・フォローボタン・投稿一覧）
- SCR-06 ユーザー検索画面（検索バー・結果一覧）

## 4. API エンドポイント案

| メソッド | パス | 説明 | 認証 |
|----------|------|------|------|
| GET | `/api/users/search?q=&cursor=&limit=` | username/表示名の部分一致検索 | 要 |
| GET | `/api/users/{username}` | プロフィール取得（follow_count, follower_count, following_by_me） | 要 |
| GET | `/api/users/{username}/posts` | 対象ユーザーの投稿一覧 | 要 |
| POST | `/api/users/{userId}/follow` | フォロー | 要 |
| DELETE | `/api/users/{userId}/follow` | フォロー解除 | 要 |

## 5. データ
- `follows`（id, follower_id, followee_id, created_at）
  - 複合 UNIQUE `(follower_id, followee_id)`、チェック制約 `follower_id <> followee_id`
- フォロー中数：`follows` を `follower_id` で `COUNT`
- フォロワー数：`follows` を `followee_id` で `COUNT`
- 検索対象：`users.username` / `users.display_name`

## 6. バリデーション / 制約
- 自分自身のフォロー：422/400 で拒否（UI ではボタン非表示）
- 二重フォロー：UNIQUE 制約で防止（冪等に扱う）
- 検索クエリ長・`limit` の上限を設ける

## 7. エラーハンドリング / 異常系
- 自分自身をフォロー要求：エラー表示
- 既フォローで再 POST / 未フォローで DELETE：冪等として扱う
- 存在しないユーザー：404
- 検索結果 0 件：「該当するユーザーがいません」を表示
