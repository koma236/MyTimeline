# 秘密情報の生成と保管。
#
# Secrets Manager ではなく SSM Parameter Store（SecureString）を使う。Secrets Manager は削除後
# 7〜30 日の復旧期間中は同名で再作成できず、destroy → apply の往復を止めてしまうため。
# Parameter Store は即時削除される。暗号鍵は AWS 管理の aws/ssm（CMK を作ると削除待機期間が生じる）。
#
# 値は Terraform が生成し、人が知る必要はない（ECS タスクが起動時に読む）。
# state にはこれらの値が平文で入るので、state ファイルはコミットしない（.gitignore 済み）。

resource "random_password" "db" {
  length  = 32
  special = true
  # RDS のマスターパスワードに使えない文字（/ @ " スペース）を除外する
  override_special = "!#$%^&*()-_=+[]{}<>:?"
}

# HS256 の署名鍵は 32 バイト以上必要（.env.example）。余裕をもって 64 文字にする
resource "random_password" "jwt" {
  length  = 64
  special = false
}

# CloudFront → ALB のオリジン検証ヘッダ（alb.tf）。
# ALB のリスナールールは * と ? をワイルドカードとして扱うので記号を含めない
resource "random_password" "origin_verify" {
  length  = 48
  special = false
}

resource "aws_ssm_parameter" "db_password" {
  name        = "/${var.project}/${var.environment}/DB_PASSWORD"
  description = "RDS master password (consumed by ECS task as DB_PASSWORD)"
  type        = "SecureString"
  value       = random_password.db.result
}

resource "aws_ssm_parameter" "jwt_secret" {
  name        = "/${var.project}/${var.environment}/JWT_SECRET"
  description = "JWT HS256 signing key (consumed by ECS task as JWT_SECRET)"
  type        = "SecureString"
  value       = random_password.jwt.result
}
