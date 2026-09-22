# VPC。public subnet に ALB と NAT Gateway、private subnet に Fargate と RDS を置く。
# ALB は 2 つ以上の AZ を要求するため、public / private とも 2 AZ 分作る。

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  # AZ 名を直書きしない。ap-northeast-1b のように新しいアカウントでは使えない AZ があるため
  azs = slice(sort(data.aws_availability_zones.available.names), 0, 2)
}

resource "aws_vpc" "main" {
  cidr_block = var.vpc_cidr
  # RDS のエンドポイント名解決と ECR pull に必要
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${var.project}-vpc" }
}

resource "aws_subnet" "public" {
  count = 2

  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone = local.azs[count.index]

  tags = { Name = "${var.project}-public-${local.azs[count.index]}" }
}

resource "aws_subnet" "private" {
  count = 2

  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 10)
  availability_zone = local.azs[count.index]

  tags = { Name = "${var.project}-private-${local.azs[count.index]}" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${var.project}-igw" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${var.project}-public" }
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.main.id
}

resource "aws_route_table_association" "public" {
  count = 2

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# NAT Gateway。private subnet の Fargate タスクが ECR / CloudWatch Logs / SSM へ出るための出口。
# 学習用途なので 1 台のみ（AZ 冗長化しない）。時間課金があるため使わないときは destroy する
resource "aws_eip" "nat" {
  domain = "vpc"

  tags = { Name = "${var.project}-nat" }
}

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id

  # IGW が先に無いと NAT の作成が失敗することがある（公式ドキュメントの推奨）
  depends_on = [aws_internet_gateway.main]

  tags = { Name = "${var.project}-nat" }
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${var.project}-private" }
}

resource "aws_route" "private_nat" {
  route_table_id         = aws_route_table.private.id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.main.id
}

resource "aws_route_table_association" "private" {
  count = 2

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# S3 への通信は NAT を通さず Gateway エンドポイントで直接届ける（無料）。
# ECR のイメージレイヤの実体は S3 なので、pull のたびに NAT のデータ転送課金が発生するのを避ける。
# 画像バケットへの PUT / DELETE も同じ経路になる
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.private.id]

  tags = { Name = "${var.project}-s3-endpoint" }
}
