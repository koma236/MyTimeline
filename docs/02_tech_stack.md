# 3. 技術スタック

> バージョンは TASKMANAGEMENT プロジェクトの技術スタックに準拠する。

## フロントエンド

| 項目 | ライブラリ / ツール | バージョン |
|------|-------------------|-----------|
| フレームワーク | React | 19.2.5 |
| 言語 | TypeScript | 6.0.2 |
| バンドラー | Vite | 8.0.10 |
| スタイリング | Tailwind CSS | 3.4.19 |
| HTTPクライアント | Axios | 1.18.1 |
| サーバー状態管理 | TanStack Query (React Query) | **未導入**（[08_constraints.md](08_constraints.md) TBD-07） |
| Lint | Oxlint | 1.71.0 |

> **サーバー状態管理について:** TanStack Query は当初の想定として記載していたが、現時点では導入していない。
> タイムラインの取得・ページングは自前のフック（`frontend/src/hooks/useTimeline.ts`）で扱っている。
> 導入するかどうかは TBD-07 として未決のまま残している。

## バックエンド

| 項目 | ライブラリ / ツール | バージョン |
|------|-------------------|-----------|
| 言語 | Java | 25 |
| フレームワーク | Spring Boot | 4.0.6 |
| 依存関係管理 | io.spring.dependency-management | 1.1.7 |
| DB アクセス | MyBatis (mybatis-spring-boot-starter) | 4.0.1 |
| DBマイグレーション | Flyway | (Spring Boot 管理) |
| 認証・認可 | Spring Security + JWT (jjwt) | Spring Boot 管理 / jjwt 0.12.6 |
| バリデーション | Bean Validation (jakarta.validation) | (Spring Boot 管理) |
| 静的解析（規約） | Checkstyle | 10.26.1 |
| 静的解析（バグ検出） | SpotBugs | 4.9.8 |
| ビルドツール | Gradle | 8.x |

## データベース・ローカル実行

| 項目 | 内容 | バージョン |
|------|------|-----------|
| データベース（ローカル） | PostgreSQL | 15 |
| コンテナランタイム | Docker + Docker Compose | - |

## ストレージ

| 項目 | 内容 | 備考 |
|------|------|------|
| 画像ストレージ | AWS S3（AWS SDK for Java v2） | 投稿画像・アバター画像を保存。DB にはキーのみ記録。詳細は [09_infrastructure.md](09_infrastructure.md) |
| 画像ストレージ（ローカル） | MinIO | S3 互換。docker-compose で起動する。アプリのコードは AWS SDK のままで、接続先の設定だけが変わる |

## 本番インフラ（AWS・暫定前提）

AWS でのサーバ構築可否は未確定だが、**ALB + EC2 + RDS + S3** を前提とした構成を想定する。
詳細は [09_infrastructure.md](09_infrastructure.md) を参照。
