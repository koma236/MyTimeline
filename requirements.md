# 要件定義書サマリ - MYTIMELINE

学習目的の X（旧 Twitter）風タイムライン型 SNS アプリ。本ファイルは要件定義の入口として、各詳細ドキュメントへのリンクをまとめる。

## 概要

- **目的:** タイムライン型 SNS の仕組みを学習しながら実装する
- **前提:** 個人利用だが複数ユーザーの利用を想定。いいね数・コメント数を可視化する
- **差別化:** インプレッション数を表示しない / リツイート機能を持たない

## 機能一覧

| ID | 機能 | 概要 | 機能定義書 |
|----|------|------|-----------|
| F01 | 認証 | 新規登録・ログイン・ログアウト | [F01_auth.md](docs/features/F01_auth.md) |
| F02 | タイムライン | 「フォロー中」「すべて」の 2 タブ | [F02_timeline.md](docs/features/F02_timeline.md) |
| F03 | 投稿・画像投稿 | テキスト＋画像（S3）・削除 | [F03_post.md](docs/features/F03_post.md) |
| F04 | コメント | コメント・件数表示・削除 | [F04_comment.md](docs/features/F04_comment.md) |
| F05 | いいね | いいね/取り消し・件数表示 | [F05_like.md](docs/features/F05_like.md) |
| F06 | フォロー・ユーザー検索 | フォロー/解除・数表示・検索 | [F06_follow.md](docs/features/F06_follow.md) |

## ドキュメント構成

| ドキュメント | 内容 |
|--------------|------|
| [docs/01_overview.md](docs/01_overview.md) | プロジェクト概要・スコープ・用語定義 |
| [docs/02_tech_stack.md](docs/02_tech_stack.md) | 技術スタック |
| [docs/03_usecases.md](docs/03_usecases.md) | ユースケース |
| [docs/04_features.md](docs/04_features.md) | 機能要件（機能一覧） |
| [docs/05_nonfunctional.md](docs/05_nonfunctional.md) | 非機能要件 |
| [docs/06_ui_design.md](docs/06_ui_design.md) | 画面設計 |
| [docs/07_er_diagram.md](docs/07_er_diagram.md) | ER 図・テーブル定義 |
| [docs/08_constraints.md](docs/08_constraints.md) | 制約・前提条件・未決事項 |
| [docs/09_infrastructure.md](docs/09_infrastructure.md) | インフラ構成（暫定・前提） |
| [docs/features/](docs/features/) | 機能単位の機能定義書（F01〜F06） |
