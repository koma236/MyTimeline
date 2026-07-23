# 機能定義書 F01: 認証（サインアップ / ログイン / ログアウト）

**関連:** 要件 [04_features.md](../04_features.md) 5.2 / ユースケース [03_usecases.md](../03_usecases.md) UC-01〜03 / 画面 [06_ui_design.md](../06_ui_design.md) SCR-01・SCR-02 / データ [07_er_diagram.md](../07_er_diagram.md) `users`

## 1. 概要 / 目的
複数ユーザーの利用を前提に、アカウント登録・ログイン・ログアウトを提供する。認証が必要な操作を保護し、投稿・コメント・いいね・フォローを本人の操作として記録できるようにする。

## 2. 機能詳細
- **新規登録:** username・表示名・メール・パスワードを入力してアカウントを作成し、そのままログイン状態にする
- **ログイン:** メール（または username）＋パスワードで認証し、認証情報（トークン/セッション）を発行する
- **ログアウト:** 認証情報を破棄し、ログイン画面へ戻す
- 未ログイン状態で認証必須画面にアクセスした場合はログイン画面へリダイレクトする

## 3. 対象画面
- SCR-01 ログイン画面
- SCR-02 新規登録画面

## 4. API エンドポイント案

| メソッド | パス | 説明 | 認証 |
|----------|------|------|------|
| POST | `/api/auth/signup` | 新規登録（username, display_name, email, password） | 不要 |
| POST | `/api/auth/login` | ログイン（email/username, password）→ トークン発行 | 不要 |
| POST | `/api/auth/logout` | ログアウト（トークン/セッション破棄） | 要 |
| GET | `/api/auth/me` | ログイン中ユーザー情報の取得 | 要 |

## 5. データ
- `users`（id, username〔UNIQUE〕, display_name, email〔UNIQUE〕, password_hash, bio, created_at, updated_at）
- パスワードは **ハッシュ化して `password_hash` に保存**（平文保存しない）

## 6. バリデーション / 制約
- username：必須・UNIQUE・半角英数字/アンダースコア・長さ制限（例: 3〜50 文字）
- display_name：必須・長さ制限（例: 1〜100 文字）
- email：必須・メール形式・UNIQUE
- password：必須・最小長（例: 8 文字以上）
- Spring Security ＋ Bean Validation で検証

## 7. エラーハンドリング / 異常系
- username / email 重複：409 相当のエラーを項目単位で表示
- 認証失敗（ログイン）：「メールアドレスまたはパスワードが正しくありません」を表示（どちらが誤りかは明示しない）
- バリデーションエラー：該当項目にエラーメッセージを表示
- 認証切れ / 未ログインで保護 API を呼んだ場合：401 を返しログイン画面へ誘導
