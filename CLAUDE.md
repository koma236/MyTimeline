# CLAUDE.md — Claude Code プロジェクト指示書

Claude Code はセッション開始時にこのファイルを自動的に読み込む。ここに記載されたルールはすべてのセッションで必ず遵守すること。

---

## プロジェクト概要

- **リポジトリ:** koma236/MyTimeline
- **目的:** X（旧 Twitter）風タイムライン型 SNS アプリ（学習目的）
- **技術スタック:** Java 25 + Spring Boot 4.0.6 / TypeScript + React 19 / PostgreSQL / Docker Compose / AWS S3（画像保存）

---

## GitHub 開発フロー（必須ルール）

### 鉄則：作業を始める前に必ず GitHub Issue を作成すること

**すべて強制：**

1. **Issue ファースト** — 対応する Issue が存在しない状態で作業を開始してはならない
2. **ブランチ必須** — Issue ごとに専用ブランチを切ること
3. **main への直接プッシュ禁止** — すべての変更は Pull Request を通じてマージすること
4. **PR は Issue を参照すること** — PR 本文に `Closes #N` を必ず含めること

---

## ステップバイステップ ワークフロー

### Step 1: GitHub Issue を作成する

```bash
gh issue create \
  --title "作業内容を簡潔に記述" \
  --label "feature"  # feature / bug / chore / documentation
```

Issue 番号（例: `#5`）を必ず控えておくこと。

### Step 2: ブランチを作成する

**ブランチ命名規則（英語）：**

| 種別 | 形式 | 例 |
|------|------|----|
| 新機能 | `feature/issue-{N}-{slug}` | `feature/issue-5-add-post-api` |
| バグ修正 | `fix/issue-{N}-{slug}` | `fix/issue-7-fix-null-pointer` |
| ドキュメント | `docs/issue-{N}-{slug}` | `docs/issue-9-update-readme` |
| その他 | `chore/issue-{N}-{slug}` | `chore/issue-11-update-deps` |

- `{N}` は Issue 番号（数字のみ）
- `{slug}` は英数字・ハイフンのみ、20文字以内推奨

```bash
git checkout -b feature/issue-{N}-{slug}
```

### Step 3: 実装・コミットする

コミットメッセージ形式: `<type>: <要約> (#N)`

- type は英語: `feat` / `fix` / `docs` / `chore` / `refactor` / `test`

```bash
git add <ファイル名>
git commit -m "feat: 投稿APIを追加 (#5)"
```

### Step 4: Pull Request を作成する

```bash
gh pr create \
  --title "変更内容の要約 (#N)" \
  --body "$(cat .github/PULL_REQUEST_TEMPLATE.md)" \
  --base main
```

PR 本文の `Closes #` 欄に Issue 番号を必ず記入すること。

### Step 5: マージする

```bash
gh pr merge <PR番号> --squash --delete-branch
```

---

## ブランチ保護ルール（GitHub 側で設定済み）

- `main` への直接プッシュ: **禁止**
- force push: **禁止**
- ブランチ削除: **禁止**
- PR なしのマージ: **禁止**
- 未解決コメントがある場合のマージ: **禁止**

---

## よくある間違いと対処法

| 間違い | 正しい対応 |
|--------|-----------|
| Issue なしで作業を始めた | 今すぐ Issue を作成し、ブランチを切り直す |
| main で作業してしまった | `git stash` → ブランチ作成 → `git stash pop` |
| PR に `Closes #N` がない | PR 本文を編集して追加する |

---

## プロジェクト固有コマンド

```bash
# バックエンドビルド
cd backend && ./gradlew build

# Docker 起動
docker-compose up -d

# ヘルスチェック
curl -s http://localhost:8080/actuator/health
```

---

## 注意事項

- `.env` ファイルはコミットしないこと（`.gitignore` 済み）
- `backend/build/` はコミットしないこと
- データベースのマイグレーションは Flyway（`backend/src/main/resources/db/migration/`）で管理すること
- 投稿画像は AWS S3 に保存し、DB には `s3_key` のみを記録すること（画像本体はコミット・DB 保存しない）
