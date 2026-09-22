# 11. インフラ構成

> IaC はすべて [terraform/](../terraform/README.md) にある。構築・デプロイ・destroy の手順もそちらを参照。
> 学習用途のため、**使わないときは destroy し、必要になったら apply で作り直す**前提で設計している（[08_constraints.md](08_constraints.md) TBD-02）。

### 11.1 構成方針

- **EC2 は使わない。** サーバ管理をなくし、コンテナ（ECS Fargate）とマネージドサービスだけで構成する
- フロントエンド（React ビルド成果物）は **S3 + CloudFront（OAC）** で配信する
- API リクエスト（`/api/*`）は CloudFront → **ALB** → **ECS Fargate 上の Spring Boot** に到達させる
- アプリの永続データは **RDS for PostgreSQL 16**（private サブネット）に保存する
- Fargate タスクは private サブネットに置き、外向き通信（ECR / CloudWatch Logs / SSM）は **NAT Gateway** を通す。S3 だけは Gateway エンドポイントで直接届ける
- 投稿画像は **S3（画像バケット）** に保存し、アプリが発行する署名付き URL で配信する（CloudFront は通さない。TBD-09）
- 秘密情報（DB パスワード・JWT 秘密鍵）は **SSM Parameter Store** に置き、タスク起動時に注入する
- リージョンは `ap-northeast-1`（東京）

### 11.2 アーキテクチャ構成図

```
                              ┌─────────────────┐
                     ユーザー │  ブラウザ        │
                              └────────┬────────┘
                                       │ HTTPS
                        ┌──────────────▼───────────────┐
                        │          CloudFront          │
                        │   /*    → S3（静的: React）   │  ← 拡張子なしのパスは CloudFront Function で /index.html に書き換え
                        │  /api/* → ALB（X-Origin-Verify 付与）│
                        └───┬──────────────────┬───────┘
                            │                  │ HTTP
          ┌─────────────────▼──┐               │           ┌────────────────┐
          │ S3（静的バケット）   │               │           │ S3（画像バケット）│ ← 署名付き URL でブラウザが直接取得
          │  React ビルド成果物  │               │           │  投稿画像 s3_key │
          └────────────────────┘               │           └───────▲────────┘
                                               │                   │ PUT / DELETE（Gateway エンドポイント）
  ┌──────────────────────── VPC ───────────────┼───────────────────┼───────────────────┐
  │  public subnet ×2            ┌─────────────▼─────────────┐     │                   │
  │                              │  ALB（CloudFront からのみ）  │   ┌─┴────────────┐     │
  │                              └─────────────┬─────────────┘   │ NAT Gateway  │─→ ECR / Logs / SSM
  │                                            │ 8080            └──────▲───────┘     │
  │  private subnet ×2           ┌─────────────▼─────────────┐          │             │
  │                              │  ECS Fargate              │──────────┘             │
  │                              │  Spring Boot（1 タスク）   │                        │
  │                              └─────────────┬─────────────┘                        │
  │                                            │ 5432                                 │
  │                              ┌─────────────▼─────────────┐                        │
  │                              │  RDS for PostgreSQL 16    │                        │
  │                              └───────────────────────────┘                        │
  └────────────────────────────────────────────────────────────────────────────────────┘
```

### 11.3 コンポーネント一覧

| コンポーネント | サービス | 役割 | 備考 |
|----------------|----------|------|------|
| CDN / 配信 | CloudFront | HTTPS 終端・静的配信・`/api/*` を ALB へ | `redirect-to-https`。`/api/*` は CachingDisabled + AllViewer（Authorization / Cookie を転送） |
| SPA フォールバック | CloudFront Function | 拡張子のないパスを `/index.html` に書き換える | カスタムエラーレスポンスは `/api/*` の 404 まで書き換えてしまうので使わない |
| 静的ホスティング | S3（静的バケット） | React ビルド成果物 | 非公開。OAC で CloudFront のみ読める |
| ロードバランサー | ALB | `/api/*` を Fargate タスクへ | SG で CloudFront の IP レンジのみ許可 + `X-Origin-Verify` ヘッダ一致でのみ転送（それ以外は 403）。ヘルスチェックは `/actuator/health/readiness`（[11_monitoring_design.md](11_monitoring_design.md) 13.2） |
| アプリ実行 | ECS Fargate | Spring Boot コンテナ（0.5 vCPU / 1 GB × 1） | private subnet。ローリング更新 + circuit breaker。ECS Exec 有効（踏み台の代わり） |
| コンテナレジストリ | ECR | バックエンドイメージ | 直近 5 世代保持 |
| データベース | RDS for PostgreSQL 16 | 永続データ | private subnet、ECS の SG からのみ。`timezone=Asia/Tokyo`（TBD-08） |
| 外向き通信 | NAT Gateway（1 台） | Fargate から ECR / CloudWatch Logs / SSM へ | 時間課金があるため使わないときは destroy |
| 画像ストレージ | S3（画像バケット） | 投稿画像・プロフィール画像の本体（DB はキーのみ） | 非公開。アプリの署名付き URL で配信 |
| 秘密情報 | SSM Parameter Store | DB パスワード・JWT 秘密鍵（Terraform が生成） | Secrets Manager は削除後の復旧期間が再構築を妨げるため使わない |
| ログ | CloudWatch Logs | アプリの JSON ログ（`/ecs/mytimeline`、14 日保持） | [10_logging_design.md](10_logging_design.md) |
| リージョン | `ap-northeast-1` | 東京リージョン | - |

### 11.4 ローカル開発での画像ストレージ

本番の S3 の代わりに、docker-compose で **S3 互換の MinIO** を起動する（`docker compose up -d minio minio-init`）。アプリのコードは AWS SDK for Java v2 のままで、接続先の環境変数だけが変わる。

| 環境変数 | ローカル | 本番（ECS タスク定義で設定済み） |
|----------|----------|----------|
| `S3_ENDPOINT` | `http://minio:9000`（compose 内）/ `http://localhost:9000`（ホスト実行） | 空（＝本物の S3） |
| `S3_PUBLIC_ENDPOINT` | `http://localhost:9000` | 空 |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | MinIO のログイン情報 | 空（ECS タスクロールを使う） |
| `S3_PATH_STYLE_ACCESS` | `true`（MinIO では必須） | `false` |

> **`S3_ENDPOINT` と `S3_PUBLIC_ENDPOINT` を分けている理由:** 署名付き URL を開くのはブラウザだが、SigV4 は Host ヘッダを署名対象に含むため、生成後に URL のホスト名を差し替えると署名が一致せず 403 になる。アプリからの到達先（`minio:9000`）とブラウザからの到達先（`localhost:9000`）が異なる以上、署名する側のエンドポイントを別に持つ必要がある。

バケットの作成はアプリ起動時ではなく使い捨てコンテナ（`minio-init`）で行う。アプリに作らせると、本番のタスクロールに `s3:CreateBucket` を与えることになり最小権限から外れるため。

### 11.5 デプロイ順序と互換性

フロントエンド（S3 + CloudFront）とバックエンド（ECR + ECS）は**別々にデプロイされる**ため、両者のバージョンが一時的にずれる。フロントは受け取った JSON を実行時に検証していない（型定義はコンパイル時のみで、`api/client.ts` は axios の型引数で受けるだけ）ので、ずれ方によっては画面が壊れる。

**運用ルール:**

| ルール | 理由 |
|--------|------|
| **バックエンド → フロントエンド**の順でデプロイする | レスポンスへのフィールド**追加**は古いフロントを壊さない。逆順にすると、新しいフロントがまだ存在しないフィールドを参照することになる |
| フィールドの**削除・リネームは 1 回のデプロイで行わない** | 古いバンドルが CloudFront とブラウザのキャッシュに残る。新旧どちらも返す期間を挟んでから消す |
| ローリング更新中は新旧のレスポンスが混在する前提で考える | ECS は新タスクが healthy になるまで旧タスクを残す（1 タスク運用でも更新中は 2 タスク並ぶ） |

**過去に起きた事象:** ローカルでバックエンドのコンテナを再ビルドし忘れ、`imageUrls` を返さない古い API に新しいフロントが繋がった。`PostCard` が `undefined` を参照して例外を投げ、React がツリー全体をアンマウントして画面が真っ白になった。

現在は `components/ErrorBoundary.tsx` を投稿カード単位と画面単位に置き、1 件の不整合が全体を巻き込まないようにしている。ただしこれは**被害を局所化する保険**であって、上記のルールを守らなくてよいという意味ではない。

### 11.6 destroy と再構築

学習用途のため、使わない期間は `terraform destroy` で全リソースを消す。再構築は `apply` → イメージ push → フロント配置の順で 20 分程度。

- RDS のデータ・画像・ECR のイメージ・ログは消える。残したいデータは destroy 前にスナップショットや退避を取る
- CloudFront のドメイン名は再構築で変わるが、CORS 設定は Terraform 内で参照しているため手作業は無い
- 削除を妨げる設定（S3 の中身、RDS の最終スナップショット、Secrets Manager の復旧期間、KMS の削除待機、ECS 自動作成のロググループ）を意図的に避けている。一覧は [terraform/README.md](../terraform/README.md)

### 11.7 将来検討事項

- 独自ドメイン（Route 53 + ACM）の導入。CloudFront ↔ ALB を HTTPS にできる。ホストゾーンと証明書は destroy の対象外にする（NS レコードの再設定を避ける）
- ECS のタスク数増加と Service Auto Scaling、NAT Gateway の AZ 冗長化、RDS の Multi-AZ 化
- CI/CD（GitHub Actions）からの自動デプロイ（OIDC で ECR push → `update-service`、S3 sync → invalidation）
- 監視基盤（Datadog / CloudWatch）の導入。アプリ側の準備は済んでおり（[10_logging_design.md](10_logging_design.md) / [11_monitoring_design.md](11_monitoring_design.md)）、`/actuator/prometheus` の公開方式（[08_constraints.md](08_constraints.md) TBD-16）を決める
- 画像配信を CloudFront + 署名付き Cookie に移し、URL を安定させてキャッシュを効かせる（TBD-09）
