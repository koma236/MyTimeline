# ALB。CloudFront の /api/* ビヘイビアからのみ受け付け、Fargate タスクへ振り分ける。
#
# CloudFront 以外からの直接アクセスは 2 段で防ぐ:
#   1. SG で CloudFront の IP レンジ以外を遮断（security_groups.tf）
#   2. CloudFront が付ける X-Origin-Verify ヘッダが一致する場合だけ転送し、それ以外は 403
# 1 だけでは他人の CloudFront ディストリビューションから叩けてしまうため 2 が要る。
#
# 独自ドメインを持たないため ALB に ACM 証明書は付けられず、CloudFront ↔ ALB は HTTP になる。
# ブラウザ ↔ CloudFront は HTTPS（cloudfront.tf）。

resource "aws_lb" "main" {
  name               = "${var.project}-alb"
  load_balancer_type = "application"
  internal           = false
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  # すぐ destroy できるよう削除保護は付けない
  enable_deletion_protection = false

  tags = { Name = "${var.project}-alb" }
}

resource "aws_lb_target_group" "app" {
  name        = "${var.project}-app"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip" # Fargate（awsvpc）はインスタンスではなく IP をターゲットにする

  # 既定 300 秒。デプロイと destroy のたびに待たされるので短縮する
  deregistration_delay = 30

  # readiness は DB 断で DOWN になるため、DB に繋がらないタスクは振り分けから外れる
  # （docs/11_monitoring_design.md 13.2）。閾値は M-01（連続 3 回失敗）と揃える
  health_check {
    path                = "/actuator/health/readiness"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = { Name = "${var.project}-app" }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  # 既定は拒否。転送はヘッダが一致するルール（下）に限る
  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "Forbidden"
      status_code  = "403"
    }
  }
}

resource "aws_lb_listener_rule" "api" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }

  condition {
    http_header {
      http_header_name = "X-Origin-Verify"
      values           = [random_password.origin_verify.result]
    }
  }
}
