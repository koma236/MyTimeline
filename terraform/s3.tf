# 投稿画像・プロフィール画像の保存先バケット。
# 配信は Spring Boot が発行する署名付き GET URL 経由のみで、公開アクセスは全遮断する。
# アプリ側はバケット名を S3_BUCKET 環境変数で受けるため、命名は自由（account_id でグローバル一意化）。
resource "aws_s3_bucket" "images" {
  bucket = "${var.project}-images-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "images" {
  bucket = aws_s3_bucket.images.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ACL を無効化し、オブジェクト所有権をバケット所有者に固定する
resource "aws_s3_bucket_ownership_controls" "images" {
  bucket = aws_s3_bucket.images.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# SSE-S3（AES256）。KMS は鍵コストと IAM 権限追加が画像用途に見合わないため使わない
resource "aws_s3_bucket_server_side_encryption_configuration" "images" {
  bucket = aws_s3_bucket.images.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# 中断されたマルチパートアップロードは不可視のまま課金され続けるため、7 日で破棄する
resource "aws_s3_bucket_lifecycle_configuration" "images" {
  bucket = aws_s3_bucket.images.id

  rule {
    id     = "abort-incomplete-multipart"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}
