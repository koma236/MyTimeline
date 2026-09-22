# ECS Fargate 用の IAM ロール。役割が違う 2 つを分ける:
#   - タスクロール（app）: アプリ自身が使う権限。S3 画像バケットの操作と ECS Exec
#   - タスク実行ロール（ecs_execution）: ECS エージェントが使う権限。ECR pull・ログ送出・SSM パラメータ取得
# どちらも ecs-tasks.amazonaws.com に AssumeRole させる。

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# ---- タスクロール ----

resource "aws_iam_role" "app" {
  name               = "${var.project}-app-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
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

# ECS Exec（aws ecs execute-command）に必要。コンテナ内の SSM エージェントがこの権限で接続する。
# リソース単位で絞れない API なので * になる
data "aws_iam_policy_document" "ecs_exec" {
  statement {
    actions = [
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "ecs_exec" {
  name   = "${var.project}-ecs-exec"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.ecs_exec.json
}

# ---- タスク実行ロール ----

resource "aws_iam_role" "ecs_execution" {
  name               = "${var.project}-ecs-execution-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

# ECR からの pull と CloudWatch Logs への書き込み（AWS 管理ポリシー）
resource "aws_iam_role_policy_attachment" "ecs_execution_managed" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# タスク定義の secrets（SSM SecureString）を起動時に読む。
# 暗号鍵が AWS 管理の aws/ssm なので kms:Decrypt の明示は不要（CMK にした場合だけ必要）
data "aws_iam_policy_document" "ecs_execution_ssm" {
  statement {
    actions = ["ssm:GetParameters"]
    resources = [
      aws_ssm_parameter.db_password.arn,
      aws_ssm_parameter.jwt_secret.arn,
    ]
  }
}

resource "aws_iam_role_policy" "ecs_execution_ssm" {
  name   = "${var.project}-ecs-execution-ssm"
  role   = aws_iam_role.ecs_execution.id
  policy = data.aws_iam_policy_document.ecs_execution_ssm.json
}
