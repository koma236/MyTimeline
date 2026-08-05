output "s3_bucket_name" {
  description = "アプリの環境変数 S3_BUCKET に設定する値"
  value       = aws_s3_bucket.images.bucket
}

output "s3_region" {
  description = "アプリの環境変数 S3_REGION に設定する値"
  value       = var.region
}

output "instance_profile_name" {
  description = "将来 EC2 を起動する際に指定するインスタンスプロファイル名"
  value       = aws_iam_instance_profile.app.name
}

output "app_role_arn" {
  description = "アプリ用 IAM ロールの ARN（確認用）"
  value       = aws_iam_role.app.arn
}
