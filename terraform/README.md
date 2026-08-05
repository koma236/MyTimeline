# Terraform — 本番用 投稿画像 S3 バケット

本番想定構成（[docs/09_infrastructure.md](../docs/09_infrastructure.md)）のうち、**S3 画像バケット関連のみ**を IaC 化している。ALB / EC2 / RDS / CloudFront は AWS サーバ構築の可否（TBD-02）が未確定のため対象外。追加する場合は本ディレクトリに `ec2.tf` などを足していく。

## 定義しているリソース

| リソース | 内容 |
|----------|------|
| `aws_s3_bucket.images` | 投稿画像・プロフィール画像の保存先。名前は `mytimeline-images-<アカウントID>`（グローバル一意対策） |
| `aws_s3_bucket_public_access_block` | 公開アクセスを全遮断（4 項目すべて true）。配信は署名付き GET URL のみ |
| `aws_s3_bucket_ownership_controls` | `BucketOwnerEnforced`（ACL 無効） |
| `aws_s3_bucket_server_side_encryption_configuration` | SSE-S3（AES256） |
| `aws_s3_bucket_lifecycle_configuration` | 未完了マルチパートアップロードを 7 日で破棄 |
| `aws_iam_role.app` ほか | EC2 用ロール。`s3:PutObject` / `GetObject` / `DeleteObject` を画像バケット配下のみに許可する最小権限 + インスタンスプロファイル |

## 前提

- Terraform `~> 1.15`
- `plan` / `apply` には AWS 認証情報が必要（環境変数または `~/.aws/credentials`）。`init` / `fmt` / `validate` は不要

## 手順

```bash
cd terraform

terraform init                    # 初回のみ（provider ダウンロード）
terraform fmt -check -recursive   # フォーマット確認
terraform validate                # 構文・参照チェック

# ここから先は AWS 認証情報を持つ人間が実行する
aws sts get-caller-identity       # 認証・対象アカウントの確認
terraform plan                    # 差分確認（新規作成のみが出ること）
terraform apply                   # 内容を確認したうえで実行
```

> **運用ルール:** `apply`（および認証を要する `plan`）は**人間が実行する**。Claude Code 等のエージェントの作業範囲は `fmt` / `validate` まで。

apply 後の確認（任意）:

```bash
aws s3api get-public-access-block --bucket "$(terraform output -raw s3_bucket_name)"   # 4 項目 true
aws s3api get-bucket-encryption   --bucket "$(terraform output -raw s3_bucket_name)"   # AES256
```

## outputs とアプリ環境変数の対応

apply 後、EC2 上の Spring Boot には以下を設定する（ローカル MinIO 用の値は [docs/09_infrastructure.md](../docs/09_infrastructure.md) 11.4 を参照）。

| 環境変数 | 本番での値 |
|----------|-----------|
| `S3_BUCKET` | `terraform output -raw s3_bucket_name` |
| `S3_REGION` | `terraform output -raw s3_region` |
| `S3_ENDPOINT` | 空（＝本物の S3） |
| `S3_PUBLIC_ENDPOINT` | 空 |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | 空（EC2 の IAM ロールで認証） |
| `S3_PATH_STYLE_ACCESS` | `false` |

EC2 を起動する際は `terraform output -raw instance_profile_name` のインスタンスプロファイルを割り当てること。

## state 管理

- state は**ローカル管理**（backend ブロックなし）。運用者 1 人・環境 1 つの現状では十分で、CI から plan を回すようになった時点で S3 backend への移行を検討する
- state ファイルにはアカウント ID 等が含まれるため**コミットしない**（`.gitignore` 済み）
- `.terraform.lock.hcl` は provider バージョンを厳密に固定するため**コミットする**
