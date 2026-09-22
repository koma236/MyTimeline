# ECS Fargate。Spring Boot をコンテナで動かす。EC2 は使わない。
#
# 依存の向き: ALB → CloudFront（オリジン）→ タスク定義（CORS に CloudFront ドメインが要る）→ サービス。
# CloudFront のドメインは作り直すたびに変わるが、参照で受け渡すので手で書き換える箇所は無い。

# ロググループは Terraform で作る。ECS に自動作成させると destroy 後に残り、
# 次の apply が「既に存在する」で失敗する
resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.project}"
  retention_in_days = var.log_retention_days

  tags = { Name = "${var.project}-app" }
}

resource "aws_ecs_cluster" "main" {
  name = "${var.project}-cluster"

  # Container Insights は有料のメトリクス。学習用途では CloudWatch 標準メトリクスで足りる
  setting {
    name  = "containerInsights"
    value = "disabled"
  }

  tags = { Name = "${var.project}-cluster" }
}

# 環境変数は docker-compose.yml の backend サービスと 1 対 1 に対応させる。
# S3_ENDPOINT などを空文字で明示するのは、application.properties の既定が
# MinIO 向け（localhost:9000 / path-style）だから。省略すると本番で MinIO を探しに行く
resource "aws_ecs_task_definition" "app" {
  family                   = "${var.project}-app"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = tostring(var.task_cpu)
  memory                   = tostring(var.task_memory)
  execution_role_arn       = aws_iam_role.ecs_execution.arn # イメージ取得・ログ送出・パラメータ取得
  task_role_arn            = aws_iam_role.app.arn           # アプリ自身が使う権限（S3 画像バケット・ECS Exec）

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  container_definitions = jsonencode([
    {
      name      = "app"
      image     = "${aws_ecr_repository.app.repository_url}:${var.image_tag}"
      essential = true

      portMappings = [
        { containerPort = 8080, protocol = "tcp" }
      ]

      # 値はすべて文字列にする（数値を混ぜると毎回差分が出る）
      environment = [
        { name = "DB_HOST", value = aws_db_instance.main.address },
        { name = "DB_PORT", value = tostring(aws_db_instance.main.port) },
        { name = "DB_NAME", value = aws_db_instance.main.db_name },
        { name = "DB_USER", value = aws_db_instance.main.username },
        { name = "JWT_ACCESS_EXPIRATION_MINUTES", value = "15" },
        { name = "JWT_REFRESH_EXPIRATION_DAYS", value = "7" },
        # ブラウザ ↔ CloudFront が HTTPS なので Cookie に Secure を付ける
        { name = "REFRESH_COOKIE_SECURE", value = "true" },
        # 配信ドメインだけを許可する（allow-credentials=true のため緩くしない）
        { name = "CORS_ALLOWED_ORIGINS", value = "https://${aws_cloudfront_distribution.main.domain_name}" },
        { name = "S3_ENDPOINT", value = "" },
        { name = "S3_PUBLIC_ENDPOINT", value = "" },
        { name = "S3_BUCKET", value = aws_s3_bucket.images.bucket },
        { name = "S3_REGION", value = var.region },
        # 空にするとタスクロールの認証情報を使う（DefaultCredentialsProvider）
        { name = "S3_ACCESS_KEY", value = "" },
        { name = "S3_SECRET_KEY", value = "" },
        { name = "S3_PATH_STYLE_ACCESS", value = "false" },
        { name = "LOG_FORMAT", value = "logstash" },
        { name = "APP_ENV", value = "production" },
        { name = "APP_VERSION", value = var.image_tag },
        { name = "LOG_LEVEL_APP", value = "INFO" },
        # API 仕様書（Swagger UI）は本番で公開しない
        { name = "SPRINGDOC_ENABLED", value = "false" },
        # docker-compose.yml と同じ理由で JST に揃える
        { name = "TZ", value = "Asia/Tokyo" },
        # コンテナのメモリ上限に対する JVM ヒープの割合
        { name = "JAVA_TOOL_OPTIONS", value = "-XX:MaxRAMPercentage=75" },
      ]

      # 秘密情報は SSM Parameter Store から起動時に注入する（ssm.tf）
      secrets = [
        { name = "DB_PASSWORD", valueFrom = aws_ssm_parameter.db_password.arn },
        { name = "JWT_SECRET", valueFrom = aws_ssm_parameter.jwt_secret.arn },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.app.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "app"
        }
      }

      # docker-compose.yml の healthcheck と同じ。liveness はプロセス生存のみを見る
      # （DB 断で readiness が落ちてもタスクを再起動しない。再起動しても DB は直らない）
      healthCheck = {
        command     = ["CMD-SHELL", "curl -fs http://localhost:8080/actuator/health/liveness || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }
  ])

  tags = { Name = "${var.project}-app" }
}

resource "aws_ecs_service" "app" {
  name            = "${var.project}-app"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  # ECS Exec（aws ecs execute-command）でコンテナに入れるようにする。踏み台 EC2 の代わり
  enable_execute_command = true

  # Spring Boot の起動（Flyway 込み）が終わるまで ALB のヘルスチェック失敗でタスクを落とさない
  health_check_grace_period_seconds = 120

  # ローリング更新。1 タスク運用なので更新中は一時的に 2 タスクになる
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  # 起動に失敗し続けるデプロイを止め、前のリビジョンに戻す。
  # 初回 apply 時は ECR が空でタスクが起動できないため、デプロイは FAILED で止まる。
  # イメージを push したら README.md の手順で再デプロイする
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # 初回 apply ではイメージが無く安定しないので、安定待ちはしない（既定 false のまま）
  wait_for_steady_state = false

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false # 外向きは NAT 経由（network.tf）
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "app"
    container_port   = 8080
  }

  # ターゲットグループがリスナー（ルール）に紐づく前にサービスを作ると失敗する
  depends_on = [aws_lb_listener_rule.api]

  tags = { Name = "${var.project}-app" }
}

# ---- DB 操作用の使い捨てタスク ----
# アプリのイメージには psql が入っていないので、必要なときだけ postgres イメージのタスクを
# run-task で起動し、ECS Exec で入って psql を使う（手順は README.md）。常駐はしない
resource "aws_ecs_task_definition" "ops" {
  family                   = "${var.project}-ops"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.app.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  container_definitions = jsonencode([
    {
      name      = "ops"
      image     = "public.ecr.aws/docker/library/postgres:16" # Docker Hub のレート制限を避ける
      essential = true
      command   = ["sleep", "infinity"]

      # psql が既定で読む環境変数名に合わせる。コンテナ内で引数なしの psql で接続できる
      environment = [
        { name = "PGHOST", value = aws_db_instance.main.address },
        { name = "PGPORT", value = tostring(aws_db_instance.main.port) },
        { name = "PGDATABASE", value = aws_db_instance.main.db_name },
        { name = "PGUSER", value = aws_db_instance.main.username },
        { name = "TZ", value = "Asia/Tokyo" },
      ]

      secrets = [
        { name = "PGPASSWORD", valueFrom = aws_ssm_parameter.db_password.arn },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.app.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "ops"
        }
      }
    }
  ])

  tags = { Name = "${var.project}-ops" }
}
