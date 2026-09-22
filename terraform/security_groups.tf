# セキュリティグループ。通信経路は CloudFront → ALB → ECS → RDS の一方向だけを許可する。
# ルールは aws_vpc_security_group_*_rule で個別に定義する（inline の ingress/egress と混ぜない）。
# inline を書かない aws_security_group には既定の allow-all egress が付かないため、egress も明示する。

# CloudFront のオリジン向け IP レンジ（AWS 管理・自動更新）。ALB へ直接アクセスされるのを防ぐ。
# このリストは SG のルール数として約 55 と数えられる（既定上限は 60/方向）ので、ALB SG の inbound には他のルールを足さない
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "alb" {
  name        = "${var.project}-alb"
  description = "ALB: allow HTTP from CloudFront only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.project}-alb" }
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_cloudfront" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTP from CloudFront origin-facing ranges"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
}

resource "aws_vpc_security_group_egress_rule" "alb_to_ecs" {
  security_group_id            = aws_security_group.alb.id
  description                  = "To Spring Boot on ECS"
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  referenced_security_group_id = aws_security_group.ecs.id
}

resource "aws_security_group" "ecs" {
  name        = "${var.project}-ecs"
  description = "ECS tasks: allow 8080 from ALB"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.project}-ecs" }
}

resource "aws_vpc_security_group_ingress_rule" "ecs_from_alb" {
  security_group_id            = aws_security_group.ecs.id
  description                  = "From ALB"
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  referenced_security_group_id = aws_security_group.alb.id
}

# ECR / CloudWatch Logs / SSM / S3 / RDS への到達に必要。private subnet なので NAT 経由になる
resource "aws_vpc_security_group_egress_rule" "ecs_all" {
  security_group_id = aws_security_group.ecs.id
  description       = "All outbound (via NAT)"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_security_group" "rds" {
  name        = "${var.project}-rds"
  description = "RDS: allow PostgreSQL from ECS tasks only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.project}-rds" }
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_ecs" {
  security_group_id            = aws_security_group.rds.id
  description                  = "PostgreSQL from ECS"
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = aws_security_group.ecs.id
}
