# Spring Boot を動かす EC2 に付与するロール。
# EC2 本体はまだ IaC 化していないが（TBD-02）、アプリに与える権限は画像バケットと
# 不可分なため先に定義しておく。EC2 追加時は instance_profile を参照するだけでよい。
data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "app" {
  name               = "${var.project}-app-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

# アプリが行う操作は PUT / presigned GET / DELETE のみ。
# ListBucket は不要（一覧操作なし）、CreateBucket はバケット作成が Terraform の責務のため付与しない。
data "aws_iam_policy_document" "images_rw" {
  statement {
    actions = [
      "s3:PutObject",
      "s3:GetObject",
      "s3:DeleteObject",
    ]
    resources = ["${aws_s3_bucket.images.arn}/*"]
  }
}

resource "aws_iam_role_policy" "images_rw" {
  name   = "${var.project}-images-rw"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.images_rw.json
}

resource "aws_iam_instance_profile" "app" {
  name = "${var.project}-app-profile"
  role = aws_iam_role.app.name
}
