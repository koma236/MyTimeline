# ---- 利用者が開く URL ----

output "app_url" {
  description = "ブラウザで開く URL（CloudFront）"
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "cloudfront_domain_name" {
  description = "CloudFront のドメイン名。アプリの CORS_ALLOWED_ORIGINS はこれを参照している"
  value       = aws_cloudfront_distribution.main.domain_name
}

output "cloudfront_distribution_id" {
  description = "フロント配置後のキャッシュ無効化（aws cloudfront create-invalidation）に使う"
  value       = aws_cloudfront_distribution.main.id
}

# ---- デプロイに使う値 ----

output "ecr_repository_url" {
  description = "docker tag / push の宛先"
  value       = aws_ecr_repository.app.repository_url
}

output "static_bucket_name" {
  description = "フロントのビルド成果物（frontend/dist）を sync する先"
  value       = aws_s3_bucket.static.bucket
}

output "ecs_cluster_name" {
  description = "aws ecs update-service / execute-command の --cluster"
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  description = "aws ecs update-service の --service"
  value       = aws_ecs_service.app.name
}

output "ops_task_definition" {
  description = "DB 操作用タスク（psql）の family。aws ecs run-task の --task-definition"
  value       = aws_ecs_task_definition.ops.family
}

output "private_subnet_ids" {
  description = "ops タスクを run-task するときの subnets"
  value       = aws_subnet.private[*].id
}

output "ecs_security_group_id" {
  description = "ops タスクを run-task するときの securityGroups"
  value       = aws_security_group.ecs.id
}

output "log_group_name" {
  description = "アプリのログ（aws logs tail）"
  value       = aws_cloudwatch_log_group.app.name
}

# ---- 確認用 ----

output "alb_dns_name" {
  description = "ALB の DNS 名（直接叩くと 403。CloudFront 経由でのみ通る）"
  value       = aws_lb.main.dns_name
}

output "rds_address" {
  description = "RDS のホスト名（private subnet 内からのみ到達可）"
  value       = aws_db_instance.main.address
}

output "s3_bucket_name" {
  description = "画像バケット名（アプリの S3_BUCKET）"
  value       = aws_s3_bucket.images.bucket
}

output "s3_region" {
  description = "アプリの S3_REGION"
  value       = var.region
}

output "app_role_arn" {
  description = "ECS タスクロールの ARN（確認用）"
  value       = aws_iam_role.app.arn
}
