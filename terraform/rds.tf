# RDS for PostgreSQL。private subnet に置き、ECS の SG からしか到達できない。
# 学習用途で「使わないときは destroy する」前提なので、削除を妨げる設定は入れない。

resource "aws_db_subnet_group" "main" {
  name       = "${var.project}-db"
  subnet_ids = aws_subnet.private[*].id

  tags = { Name = "${var.project}-db" }
}

# created_at / updated_at はタイムゾーン無しの TIMESTAMP で保存しているため、
# DB 側の時刻を JST に揃える（docker-compose.yml の db サービスと同じ理由。docs/08_constraints.md TBD-08）
resource "aws_db_parameter_group" "main" {
  name   = "${var.project}-postgres16"
  family = "postgres16"

  parameter {
    name  = "timezone"
    value = "Asia/Tokyo"
  }

  tags = { Name = "${var.project}-postgres16" }
}

resource "aws_db_instance" "main" {
  identifier = "${var.project}-db"

  engine = "postgres"
  # メジャーのみ指定し、マイナーは AWS に任せる（自動マイナーアップグレード）
  engine_version             = "16"
  auto_minor_version_upgrade = true
  instance_class             = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true # AWS 管理鍵（aws/rds）。CMK は使わない

  # スキーマは起動時の Flyway が作るため、DB 名とユーザーだけ用意する。
  # 値はローカル（.env.example）と揃えて、環境差で迷わないようにする
  db_name  = "mytimeline"
  username = "mytimelineuser"
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.main.name
  publicly_accessible    = false
  multi_az               = false

  # 自動バックアップは 1 日。0 でも動くが、割当ストレージ分までのバックアップは無料なので残す
  backup_retention_period = 1

  # ---- destroy をすぐ通すための設定 ----
  # 最終スナップショットを取らない（取ると destroy 後もスナップショットが残り課金される）。
  # データは消える。残したいときは destroy 前に手動でスナップショットを取る（README.md）
  skip_final_snapshot = true
  deletion_protection = false
  # 変更をメンテナンスウィンドウまで待たない
  apply_immediately = true

  performance_insights_enabled = false

  tags = { Name = "${var.project}-db" }
}
