# 3. 技術スタック

> バージョンは TASKMANAGEMENT プロジェクトの技術スタックに準拠する。

## フロントエンド

| 項目 | ライブラリ / ツール | バージョン |
|------|-------------------|-----------|
| フレームワーク | React | 19.2.5 |
| 言語 | TypeScript | 6.0.2 |
| バンドラー | Vite | 8.0.10 |
| スタイリング | Tailwind CSS | 3.4.19 |
| HTTPクライアント | Axios | 1.15.2 |
| サーバー状態管理 | TanStack Query (React Query) | 5.100.5 |

## バックエンド

| 項目 | ライブラリ / ツール | バージョン |
|------|-------------------|-----------|
| 言語 | Java | 25 |
| フレームワーク | Spring Boot | 4.0.6 |
| 依存関係管理 | io.spring.dependency-management | 1.1.7 |
| ORM | Spring Data JPA + Hibernate | (Spring Boot 管理) |
| DBマイグレーション | Flyway | (Spring Boot 管理) |
| 認証・認可 | Spring Security | (Spring Boot 管理) |
| バリデーション | Bean Validation (jakarta.validation) | (Spring Boot 管理) |
| ビルドツール | Gradle | 8.x |

## データベース・ローカル実行

| 項目 | 内容 | バージョン |
|------|------|-----------|
| データベース（ローカル） | PostgreSQL | 15 |
| コンテナランタイム | Docker + Docker Compose | - |

## ストレージ

| 項目 | 内容 | 備考 |
|------|------|------|
| 画像ストレージ | AWS S3 | 投稿画像を保存。DB には `s3_key` のみ記録。詳細は [09_infrastructure.md](09_infrastructure.md) |

## 本番インフラ（AWS・暫定前提）

AWS でのサーバ構築可否は未確定だが、**ALB + EC2 + RDS + S3** を前提とした構成を想定する。
詳細は [09_infrastructure.md](09_infrastructure.md) を参照。
