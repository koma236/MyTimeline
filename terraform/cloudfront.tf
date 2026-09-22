# CloudFront。ブラウザが到達する唯一の入口で、HTTPS 終端を担う。
#   /*      → 静的バケット（React ビルド成果物）。OAC で CloudFront だけが読める
#   /api/*  → ALB（alb.tf）。キャッシュせず全ヘッダ・Cookie を転送する
# 画像は現状どおりアプリが発行する S3 の署名付き URL で配信し、CloudFront を通さない（docs/08_constraints.md TBD-09）。

# ---- 静的バケット（画像バケットと同じく非公開・SSE-S3）----

resource "aws_s3_bucket" "static" {
  bucket = "${var.project}-static-${data.aws_caller_identity.current.account_id}"
  # destroy 時に中身（ビルド成果物）ごと消す
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "static" {
  bucket = aws_s3_bucket.static.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "static" {
  bucket = aws_s3_bucket.static.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "static" {
  bucket = aws_s3_bucket.static.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# CloudFront からのみ GetObject を許可する。SourceArn で自分のディストリビューションに限定する
data "aws_iam_policy_document" "static_bucket" {
  statement {
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.static.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.main.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "static" {
  bucket = aws_s3_bucket.static.id
  policy = data.aws_iam_policy_document.static_bucket.json

  depends_on = [aws_s3_bucket_public_access_block.static]
}

resource "aws_cloudfront_origin_access_control" "static" {
  name                              = "${var.project}-static"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# ---- SPA のフォールバック ----
# BrowserRouter のパス（/users/xxx など）はバケットに実体が無い。
# カスタムエラーレスポンス（403/404 → /index.html）はディストリビューション全体に効き、
# /api/* の 404 まで index.html に化けてフロントの JSON エラー処理を壊すので使わない。
# 代わりに静的側のビヘイビアだけで、拡張子の無いパスを /index.html に書き換える。
# username は英数字と _ のみ、投稿 id は数値なので、この判定で壊れる画面は無い
resource "aws_cloudfront_function" "spa_rewrite" {
  name    = "${var.project}-spa-rewrite"
  runtime = "cloudfront-js-2.0"
  publish = true
  code    = <<-EOT
    function handler(event) {
      var request = event.request;
      var lastSegment = request.uri.split('/').pop();
      if (!lastSegment.includes('.')) {
        request.uri = '/index.html';
      }
      return request;
    }
  EOT
}

# ---- マネージドポリシー（ID を直書きせず名前で引く）----

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

# Authorization・Cookie・クエリをすべてオリジンへ渡す。
# Authorization ヘッダは CachingDisabled と組み合わせたときに転送される
data "aws_cloudfront_origin_request_policy" "all_viewer" {
  name = "Managed-AllViewer"
}

# ---- ディストリビューション ----

resource "aws_cloudfront_distribution" "main" {
  enabled             = true
  comment             = "${var.project} (static + /api/* to ALB)"
  is_ipv6_enabled     = true
  http_version        = "http2and3"
  default_root_object = "index.html"
  # 日本を含む価格クラス（PriceClass_100 は北米・欧州のみ）
  price_class = "PriceClass_200"

  origin {
    origin_id                = "s3-static"
    domain_name              = aws_s3_bucket.static.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.static.id
  }

  origin {
    origin_id   = "alb-api"
    domain_name = aws_lb.main.dns_name

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only" # ALB に証明書が無いため（alb.tf）
      origin_ssl_protocols   = ["TLSv1.2"]
    }

    # ALB のリスナールールがこのヘッダを検証する（alb.tf）
    custom_header {
      name  = "X-Origin-Verify"
      value = random_password.origin_verify.result
    }
  }

  default_cache_behavior {
    target_origin_id       = "s3-static"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_rewrite.arn
    }
  }

  ordered_cache_behavior {
    path_pattern             = "/api/*"
    target_origin_id         = "alb-api"
    viewer_protocol_policy   = "https-only"
    allowed_methods          = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods           = ["GET", "HEAD"]
    compress                 = true
    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # 独自ドメインを持たないので *.cloudfront.net の既定証明書を使う
  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = { Name = "${var.project}-cdn" }
}
