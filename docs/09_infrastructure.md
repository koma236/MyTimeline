# 11. インフラ構成（暫定・前提）

> **重要（前提）:** AWS でのサーバ構築を実際に行うかは**未確定**（[08_constraints.md](08_constraints.md) TBD-02）。
> 本ドキュメントは、構築する場合に想定する **ALB + EC2 + RDS + S3** 構成を先行して整理したものであり、確定した本番構成ではない。
> IaC は S3 画像バケットと EC2 用 IAM のみ [terraform/](../terraform/README.md) で定義済み。それ以外の IaC・詳細なデプロイ手順は未着手（TBD-03）。

### 11.1 構成方針

- フロントエンド（React ビルド成果物）は **S3 静的ホスティング＋CloudFront** で配信する
- API リクエスト（`/api/*`）は **ALB** を経由して **EC2 上の Spring Boot** に到達させる
- アプリの永続データは **RDS for PostgreSQL**（private サブネット）に保存する
- 投稿画像は **S3（画像バケット）** に保存し、**CloudFront** 経由で配信する
- リージョンは `ap-northeast-1`（東京）を想定

### 11.2 アーキテクチャ構成図

```
                              ┌─────────────────┐
                     ユーザー │  ブラウザ        │
                              └────────┬────────┘
                                       │ HTTPS
                        ┌──────────────▼───────────────┐
                        │          CloudFront          │
                        │   /*   → S3（静的: React）    │
                        │  /api/* → ALB                │
                        │  画像   → S3（画像バケット）   │
                        └───┬───────────┬──────────┬────┘
                            │           │          │
          ┌─────────────────▼──┐   ┌────▼─────┐  ┌─▼──────────────┐
          │ S3 (静的ホスティング)│   │   ALB    │  │ S3(画像バケット) │
          │  React ビルド成果物  │   └────┬─────┘  │  投稿画像 s3_key │
          └────────────────────┘        │        └────────────────┘
                                         │  /api/*
  ┌──────────────────────── VPC ─────────┼───────────────────────────┐
  │  public subnet                       │                            │
  │                              ┌────────▼─────────┐                  │
  │                              │  EC2             │                  │
  │                              │  Spring Boot     │                  │
  │                              │  (Nginx/systemd) │                  │
  │                              └────────┬─────────┘                  │
  │  private subnet                       │                            │
  │                              ┌────────▼─────────┐                  │
  │                              │ RDS (PostgreSQL) │                  │
  │                              └──────────────────┘                  │
  └────────────────────────────────────────────────────────────────────┘
```

### 11.3 コンポーネント一覧

| コンポーネント | サービス | 役割 | 備考 |
|----------------|----------|------|------|
| CDN / 配信 | CloudFront | 静的配信・`/api/*` を ALB へ・画像配信・HTTPS 終端 | `redirect-to-https` |
| 静的ホスティング | S3 | React ビルド成果物を配信 | 直接公開せず CloudFront (OAC) 経由 |
| ロードバランサー | ALB | `/api/*` を EC2 へルーティング。将来の複数台構成に対応 | ヘルスチェックは `/actuator/health/readiness`（DB 断で振り分けから外れる。[11_monitoring_design.md](11_monitoring_design.md) 13.2） |
| アプリ実行 | EC2 | Spring Boot アプリを実行 | public subnet、将来 Auto Scaling を検討 |
| データベース | RDS for PostgreSQL | 永続データ（users/posts/... ） | private subnet、外部非公開 |
| 画像ストレージ | S3（画像バケット） | 投稿画像・プロフィール画像の本体を保存（DB はキーのみ） | [terraform/](../terraform/README.md) で定義済み。現状は署名付き URL で配信。将来 CloudFront (OAC) 経由へ |
| リージョン | `ap-northeast-1` | 東京リージョン | - |

### 11.4 ローカル開発での画像ストレージ

本番の S3 の代わりに、docker-compose で **S3 互換の MinIO** を起動する（`docker compose up -d minio minio-init`）。アプリのコードは AWS SDK for Java v2 のままで、接続先の環境変数だけが変わる。

| 環境変数 | ローカル | 本番 |
|----------|----------|------|
| `S3_ENDPOINT` | `http://minio:9000`（compose 内）/ `http://localhost:9000`（ホスト実行） | 空（＝本物の S3） |
| `S3_PUBLIC_ENDPOINT` | `http://localhost:9000` | 空 |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | MinIO のログイン情報 | 空（EC2 の IAM ロールを使う） |
| `S3_PATH_STYLE_ACCESS` | `true`（MinIO では必須） | `false` でよい |

> **`S3_ENDPOINT` と `S3_PUBLIC_ENDPOINT` を分けている理由:** 署名付き URL を開くのはブラウザだが、SigV4 は Host ヘッダを署名対象に含むため、生成後に URL のホスト名を差し替えると署名が一致せず 403 になる。アプリからの到達先（`minio:9000`）とブラウザからの到達先（`localhost:9000`）が異なる以上、署名する側のエンドポイントを別に持つ必要がある。

バケットの作成はアプリ起動時ではなく使い捨てコンテナ（`minio-init`）で行う。アプリに作らせると、本番の IAM ロールに `s3:CreateBucket` を与えることになり最小権限から外れるため。

### 11.5 デプロイ順序と互換性

フロントエンド（S3 + CloudFront）とバックエンド（ALB + EC2）は**別々にデプロイされる**ため、両者のバージョンが一時的にずれる。フロントは受け取った JSON を実行時に検証していない（型定義はコンパイル時のみで、`api/client.ts` は axios の型引数で受けるだけ）ので、ずれ方によっては画面が壊れる。

**運用ルール:**

| ルール | 理由 |
|--------|------|
| **バックエンド → フロントエンド**の順でデプロイする | レスポンスへのフィールド**追加**は古いフロントを壊さない。逆順にすると、新しいフロントがまだ存在しないフィールドを参照することになる |
| フィールドの**削除・リネームは 1 回のデプロイで行わない** | 古いバンドルが CloudFront とブラウザのキャッシュに残る。新旧どちらも返す期間を挟んでから消す |
| EC2 を複数台にしたら、ローリング更新中は新旧のレスポンスが混在する前提で考える | リクエストごとに応答の形が変わりうる |

**過去に起きた事象:** ローカルでバックエンドのコンテナを再ビルドし忘れ、`imageUrls` を返さない古い API に新しいフロントが繋がった。`PostCard` が `undefined` を参照して例外を投げ、React がツリー全体をアンマウントして画面が真っ白になった。

現在は `components/ErrorBoundary.tsx` を投稿カード単位と画面単位に置き、1 件の不整合が全体を巻き込まないようにしている。ただしこれは**被害を局所化する保険**であって、上記のルールを守らなくてよいという意味ではない。

### 11.6 将来検討事項

- EC2 の複数台構成＋Auto Scaling による可用性・スケーラビリティ向上
- RDS の Multi-AZ 化・自動バックアップ運用
- Terraform による IaC 化の拡大（S3 画像バケット + IAM は [terraform/](../terraform/README.md) で定義済み。EC2 / RDS / ALB / CloudFront が未着手）、CI/CD（GitHub Actions 等）での自動デプロイ
- 独自ドメイン（Route 53 + ACM）の導入
- 監視・ログ基盤（Datadog / CloudWatch）の導入。アプリ側の準備は済んでおり（[10_logging_design.md](10_logging_design.md) / [11_monitoring_design.md](11_monitoring_design.md)）、エージェントの配置と `/actuator/prometheus` の公開方式（[08_constraints.md](08_constraints.md) TBD-16）を決める
