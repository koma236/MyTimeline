---
name: quality-check
description: このリポジトリ全体のコード品質チェックと修正を行う。フロントエンド（Oxlint / tsc / Vitest）、バックエンド（Gradle build / Checkstyle / SpotBugs）、インフラ（Terraform）、およびドキュメントと実装の整合性を確認する。
allowed-tools: Bash, Read, Edit, Write, Glob, Grep
---

# Quality Check

React + Spring Boot プロジェクトの全体的なコード品質チェックと修正を行う。

> もとはユーザーのグローバル設定（`~/.claude/commands/quality-check.md`）にあったものを、
> チーム内で共有しレビュー対象にするためリポジトリへ持ち込んだ。
> コマンド名はこのリポジトリの実際の構成（Oxlint / Vitest / SpotBugs）に合わせてある。

## 実行内容

### フロントエンド (React / TypeScript)

作業ディレクトリは `frontend/`。

1. `npm run lint` を実行して Oxlint のエラー・警告を確認・修正する（`--max-warnings=0`）
2. `npm run typecheck` で型エラーがないことを確認する
3. `npm run test` で Vitest が全件通ることを確認する
4. まとめて確認する場合は `npm run check`（`lint` → `typecheck` → `test`）
5. 以下の観点でコードをレビューし、問題があれば修正する:
   - React hooks の正しい使用（useEffect 内の setState、依存配列の漏れなど）
   - TypeScript の型安全性（unsafe キャスト `as Type`、non-null assertion `!` の乱用など）
   - Oxlint の `categories` が `correctness` / `suspicious` / `perf` を維持しているか。
     ルールを無効化する場合は `.oxlintrc.json` に理由をコメントで残しているか
   - 壊れても画面上は気付きにくいロジック（`useCursorPager` の追い越しレスポンス破棄・二重取得抑止、
     `client.ts` の 401 リフレッシュ集約、`AuthProvider` の状態遷移）にテストが残っているか
6. `npm run test:coverage` で分岐カバレッジを確認する（`coverage/index.html`）。閾値で落とす運用ではなく、
   新しく足したコードに未到達の分岐が無いかを見る。到達不能な防御コードは理由をテストのコメントに残す
7. テストは設計技法（同値分割 / 境界値 / デシジョンテーブル / 状態遷移 / 分岐網羅）からケースを導き、
   `it()` 名やファイル冒頭コメントに技法を明記する。pages のテストは API モジュールを `vi.mock` し、
   `src/test/renderWithProviders.tsx` と `src/test/fixtures.ts` を使ってユーザー操作ベースで書く

### バックエンド (Spring Boot / Java)

作業ディレクトリは `backend/`。

1. `./gradlew build` を実行してコンパイルエラー・テスト失敗がないことを確認する
2. `./gradlew checkstyleMain checkstyleTest` を実行して Checkstyle 違反を確認・修正する
3. SpotBugs の検出結果を確認する（`build` に含まれる）
4. テストは 3 層で構成されている。変更した層に対応するテストがあるか確認する:
   - Mapper（`@MybatisTest` + H2、`mapper/*MapperTest`）: SQL・制約（UNIQUE / CHECK / CASCADE）・カーソルの向き
   - Service / Controller（モック）: 業務ルールとバリデーションの境界値
   - 結合（`@SpringBootTest` + H2、`integration/*IntegrationTest`）: Controller → DB を通した状態遷移。
     S3 だけ `@MockitoBean`。docker-compose を止めた状態でも通ること（開発 DB に依存しない）
5. JaCoCo の分岐カバレッジを確認する（`build/reports/jacoco/test/html/index.html`、CSV は
   `jacocoTestReport.csv`）。閾値では落とさず、新しく足したコードの未到達分岐を探す材料にする
6. 以下の観点でコードをレビューし、問題があれば修正する:
   - コントローラーで手動 try-catch を使わず `@RestControllerAdvice` に集中させているか
   - `@Valid` アノテーションがリクエストボディに付与されているか
   - サービス層で DTO と重複したバリデーションロジックがないか
   - SLF4J ロギングが主要な CRUD 操作に追加されているか
   - スターインポート (`.*`) が使われていないか

### インフラ (Terraform)

作業ディレクトリは `terraform/`（S3 画像バケット + EC2 用 IAM を定義。手順は `terraform/README.md`）。

1. `terraform fmt -check -recursive` を実行してフォーマット違反を確認し、`terraform fmt -recursive` で修正する
2. `terraform validate` を実行して構文・参照エラーがないことを確認する（必要に応じて先に `terraform init`）
3. `terraform plan` を実行し、`.tf` ファイルと state の差分（ドリフト）がないことを確認する（"No changes" を期待。意図しない差分があれば原因を調査）。AWS 認証情報がない環境では実行できないためスキップし、その旨を報告する
4. 以下の観点でコードをレビューし、問題があれば修正する:
   - シークレットや認証情報（DB パスワード、API キーなど）が `.tf` にハードコードされていないか。`terraform.tfvars` 等の機密ファイルは `.gitignore` 対象になっているか
   - リソース参照が文字列補間 `"${aws_x.y.id}"` ではなく属性参照 `aws_x.y.id` になっているか
   - プロバイダと Terraform 本体のバージョンが `required_providers` / `required_version` で固定されているか
   - 主要リソースに `tags = { Name = ... }` などタグ付けされているか
   - S3 / RDS 等で意図しない public access が許可されていないか（`block_public_acls` 等の確認）
   - セキュリティグループの ingress に `0.0.0.0/0` を必要以上に許可していないか
   - user_data やインラインスクリプト内のシェル設定が、OS 既定との衝突を考慮しているか（例: Nginx の `default_server` 競合）

### ドキュメント整合性

実装とドキュメントの乖離を確認し、**実装を正**としてドキュメントを修正する:

- `README.md` / `frontend/README.md`: 前提バージョン、API エンドポイント一覧、ポート番号、npm スクリプト一覧
- ER 図: テーブル名・カラム定義が `backend/src/main/resources/db/migration/` の SQL と一致しているか
- `requirements.md` / `docs/`: 技術スタックのバージョンが実装と一致しているか

### CI との対応

`.github/workflows/quality-check.yml` が PR ごとに上記のフロントエンド 1〜4 とバックエンド 1〜3 を実行し、
テスト結果とカバレッジをアーティファクトに保存する。手元でこのスキルを通していれば CI も通る。

## 完了条件

- `cd frontend && npm run check` が成功（Oxlint 0 件 / 型エラー 0 件 / Vitest 全件パス）
- `cd backend && ./gradlew build` 成功（Checkstyle・SpotBugs 警告 0 件）
- `.tf` がある場合: `terraform fmt -check -recursive` 差分 0 件、`terraform validate` 成功、
  `terraform plan` で意図しない差分が出ないこと（あれば説明可能であること）
- ドキュメントと実装の乖離が解消されている

## 注意

修正を入れた場合は、CLAUDE.md の GitHub 開発フローに従うこと（Issue → ブランチ → PR）。
**マージはしない。** PR の作成までで止め、URL を報告すること。
