# Terraform — 本番インフラ（CloudFront + S3 + ALB + ECS Fargate + RDS）

[docs/09_infrastructure.md](../docs/09_infrastructure.md) の構成をすべて IaC 化している。EC2 は使わない。
学習用途のため、**使わないときは `terraform destroy` し、必要になったら `terraform apply` で作り直す**運用を前提に、往復を止める設定を入れていない（[destroy と再構築](#destroy-と再構築)）。

```
Browser ─HTTPS→ CloudFront ┬─ /*      → S3 静的バケット（React ビルド成果物、OAC）
                           ├─ /api/*  → ALB ─HTTP→ ECS Fargate（Spring Boot :8080）→ RDS PostgreSQL 16
                           └─ 画像    → S3 画像バケット（アプリ発行の署名付き URL。CloudFront を通さない）

VPC  public subnet ×2 : ALB / NAT Gateway（1 台）
     private subnet ×2: Fargate タスク / RDS      → 外向きは NAT 経由、S3 だけ Gateway エンドポイント
```

## ファイルとリソース

| ファイル | 内容 |
|----------|------|
| `network.tf` | VPC、public / private サブネット（2 AZ）、Internet Gateway、NAT Gateway（1 台）、ルートテーブル、S3 Gateway エンドポイント |
| `security_groups.tf` | ALB（CloudFront のプレフィックスリストからの 80 のみ）→ ECS（ALB からの 8080 のみ）→ RDS（ECS からの 5432 のみ） |
| `alb.tf` | ALB、ターゲットグループ（ヘルスチェック `/actuator/health/readiness`）、リスナー。既定は 403 で、CloudFront が付ける `X-Origin-Verify` ヘッダが一致した場合だけ転送 |
| `ecs.tf` | クラスタ、タスク定義（環境変数は docker-compose.yml の backend と対応）、サービス（1 タスク、ECS Exec 有効）、ロググループ、DB 操作用の `ops` タスク定義 |
| `ecr.tf` | バックエンドイメージのリポジトリ（`force_delete`、直近 5 世代保持） |
| `rds.tf` | RDS for PostgreSQL 16（db.t4g.micro、private、`timezone=Asia/Tokyo`、`skip_final_snapshot`） |
| `ssm.tf` | DB パスワード・JWT 秘密鍵・オリジン検証ヘッダの生成と SSM Parameter Store（SecureString）への保管 |
| `cloudfront.tf` | 静的バケット、OAC、SPA フォールバック用 CloudFront Function、ディストリビューション |
| `s3.tf` | 画像バケット（非公開、SSE-S3、未完了マルチパートの破棄） |
| `iam.tf` | タスクロール（画像バケット操作 + ECS Exec）、タスク実行ロール（ECR pull、ログ、SSM 取得） |
| `outputs.tf` | デプロイ・確認に使う値（URL、ECR、バケット名、クラスタ名など） |

## 前提

- Terraform `~> 1.15`、AWS CLI v2、Docker、Node.js（フロントのビルド）
- ECS Exec を使うなら `session-manager-plugin`（`brew install --cask session-manager-plugin`）
- `plan` / `apply` / `destroy` には AWS 認証情報が必要（環境変数または `~/.aws/credentials`）。`init` / `fmt` / `validate` は不要

> **運用ルール:** `apply` / `destroy`（および認証を要する `plan`）は**人間が実行する**。Claude Code 等のエージェントの作業範囲は `fmt` / `validate` まで。

## 手順

### 1. 構文チェック（誰でも）

```bash
cd terraform
terraform init                    # 初回と provider 追加時
terraform fmt -check -recursive
terraform validate
```

### 2. 構築（人間）

```bash
aws sts get-caller-identity       # 認証・対象アカウントの確認
terraform plan                    # 差分確認
terraform apply                   # 15〜20 分（RDS と CloudFront が大半）
```

apply 直後は ECR が空なので ECS タスクは起動できず、デプロイは circuit breaker で FAILED になる。`/api/*` は 503 を返す。これは正常で、次の手順でイメージを push すると動き出す。

### 3. バックエンドのイメージを push（人間）

Fargate は既定で `X86_64`（`variables.tf` の `cpu_architecture`）。Apple Silicon で build するときは `--platform linux/amd64` が必須（付けないとタスクが `exec format error` で落ちる）。

```bash
REGION=$(terraform output -raw s3_region)
ECR=$(terraform output -raw ecr_repository_url)

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "${ECR%%/*}"
docker build --platform linux/amd64 -t "$ECR:latest" ../backend
docker push "$ECR:latest"

# push したイメージで再デプロイし、安定するまで待つ
aws ecs update-service --cluster "$(terraform output -raw ecs_cluster_name)" \
  --service "$(terraform output -raw ecs_service_name)" --force-new-deployment --no-cli-pager
aws ecs wait services-stable --cluster "$(terraform output -raw ecs_cluster_name)" \
  --services "$(terraform output -raw ecs_service_name)"
```

### 4. フロントエンドを配置（人間）

バックエンドが安定してから行う（[docs/09_infrastructure.md](../docs/09_infrastructure.md) 11.5 のデプロイ順序）。

```bash
BUCKET=$(terraform output -raw static_bucket_name)
DIST_ID=$(terraform output -raw cloudfront_distribution_id)

(cd ../frontend && npm ci && npm run build)
aws s3 sync ../frontend/dist "s3://$BUCKET" --delete
# index.html だけはキャッシュさせない（assets はハッシュ付きファイル名なので長期キャッシュでよい）
aws s3 cp ../frontend/dist/index.html "s3://$BUCKET/index.html" --cache-control "no-cache"
aws cloudfront create-invalidation --distribution-id "$DIST_ID" --paths "/index.html" --no-cli-pager
```

### 5. 動作確認

```bash
APP_URL=$(terraform output -raw app_url)
curl -s -o /dev/null -w '%{http_code}\n' "$APP_URL/"                     # 200（index.html）
curl -s -o /dev/null -w '%{http_code}\n' "$APP_URL/users/someone"        # 200（SPA フォールバック）
curl -s "$APP_URL/api/timeline/all"                                     # 401 JSON（未ログイン。ALB まで通っている）
curl -s -o /dev/null -w '%{http_code}\n' "http://$(terraform output -raw alb_dns_name)/api/timeline/all"  # タイムアウト or 403（直接は通らない）

# アプリのログ（JSON 1 行）
aws logs tail "$(terraform output -raw log_group_name)" --follow --format short
```

ブラウザで `app_url` を開き、新規登録 → 投稿（画像付き）→ プロフィール画像の変更まで通れば、RDS・S3・署名付き URL・Cookie の各経路が動いている。

### 6. DB を直接操作する（psql）

踏み台 EC2 は無い。DB 操作用のタスク（postgres イメージ）を必要なときだけ起動し、ECS Exec で入る。

```bash
CLUSTER=$(terraform output -raw ecs_cluster_name)
SUBNET=$(terraform output -json private_subnet_ids | jq -r '.[0]')
SG=$(terraform output -raw ecs_security_group_id)

TASK=$(aws ecs run-task --cluster "$CLUSTER" --launch-type FARGATE --enable-execute-command \
  --task-definition "$(terraform output -raw ops_task_definition)" \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNET],securityGroups=[$SG],assignPublicIp=DISABLED}" \
  --query 'tasks[0].taskArn' --output text)
aws ecs wait tasks-running --cluster "$CLUSTER" --tasks "$TASK"

aws ecs execute-command --cluster "$CLUSTER" --task "$TASK" --container ops --interactive --command psql
# 接続先・認証情報は環境変数（PGHOST / PGPASSWORD 等）で渡っているので引数不要

aws ecs stop-task --cluster "$CLUSTER" --task "$TASK" --no-cli-pager   # 終わったら止める
```

アプリのコンテナ自体に入りたいときは `--container app --command /bin/bash` で同様に入れる（`TASK` はサービスのタスク ARN）。

## destroy と再構築

```bash
terraform destroy                 # 10〜15 分
```

### 消えるもの・残るもの

| 対象 | destroy 後 | 備考 |
|------|-----------|------|
| RDS のデータ | **消える** | 残したいときは destroy 前に `aws rds create-db-snapshot`。再構築後に手動で復元する |
| 画像バケット・静的バケットの中身 | **消える** | `force_destroy` |
| ECR のイメージ | **消える** | `force_delete`。再構築後に手順 3 で push し直す |
| SSM パラメータ（DB パスワード・JWT 秘密鍵） | 消える（即時） | 再構築時に新しい値が生成される。発行済みのリフレッシュトークンは無効になる |
| CloudWatch Logs | 消える | ロググループを Terraform 管理にしているため |
| CloudFront のドメイン名 | **再構築で変わる** | CORS 設定は Terraform 内で参照しているので手作業は無い。ブックマークだけ更新する |
| state ファイル（`terraform.tfstate`） | 残る（空になる） | **消さないこと**。再 apply に必要 |

### 往復を止めないための設計

| 設定 | 理由 |
|------|------|
| S3 `force_destroy = true`（両バケット） | 中身があると削除に失敗する |
| ECR `force_delete = true` | イメージがあると削除に失敗する |
| RDS `skip_final_snapshot = true` / `deletion_protection = false` | 最終スナップショット待ちや保護で destroy が止まる・スナップショットが残って課金される |
| ALB `enable_deletion_protection = false` | 同上 |
| Secrets Manager ではなく SSM Parameter Store | Secrets Manager は削除後 7〜30 日間、同名で再作成できない |
| KMS CMK を作らない（S3 / RDS / SSM とも AWS 管理鍵） | CMK は削除待機期間がある |
| ロググループを Terraform で作る | ECS に自動作成させると destroy 後も残り、再 apply が「既に存在する」で失敗する |
| CloudFront ドメインを参照で受け渡す | 再構築で変わっても `.tf` を編集しない |
| ターゲットグループ `deregistration_delay = 30` | 既定 300 秒のドレイン待ちを短縮 |

### 再構築の順序

手順 2 → 3 → 4 をそのまま繰り返す。apply 後にイメージ push、その後にフロント配置。

## 料金の目安（起動中）

NAT Gateway・ALB・Fargate・RDS は起動しているだけで時間課金される（合計で月 1 万円前後）。**使わない期間は destroy する。**

## state 管理

- state は**ローカル管理**（backend ブロックなし）。運用者 1 人・環境 1 つの現状では十分で、CI から plan を回すようになった時点で S3 backend への移行を検討する
- state には DB パスワードや JWT 秘密鍵が平文で入る。**コミットしない**（`.gitignore` 済み）
- `.terraform.lock.hcl` は provider バージョンを厳密に固定するため**コミットする**。provider を追加したら `terraform init` 後の差分もコミットする
