# バックエンドのコンテナイメージ置き場。
# destroy 時にイメージが残っていても削除できるよう force_delete を付ける。
# イメージは再構築後に push し直す（手順は README.md）
resource "aws_ecr_repository" "app" {
  name                 = "${var.project}-backend"
  image_tag_mutability = "MUTABLE" # latest タグを上書きする運用のため
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = { Name = "${var.project}-backend" }
}

# 古いイメージを溜めない。ストレージ課金と一覧の見やすさのため直近 5 世代だけ残す
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 5 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = { type = "expire" }
    }]
  })
}
