variable "project" {
  description = "リソース名・タグに使うプロジェクト名"
  type        = string
  default     = "mytimeline"
}

variable "environment" {
  description = "環境名（タグ・SSM パラメータのパス用）"
  type        = string
  default     = "prod"
}

variable "region" {
  description = "AWS リージョン。アプリの S3_REGION と一致させる"
  type        = string
  default     = "ap-northeast-1"
}

variable "vpc_cidr" {
  description = "VPC の CIDR。/24 を 8 ビットで切り出して public 2 つ・private 2 つのサブネットにする"
  type        = string
  default     = "10.0.0.0/16"
}

variable "image_tag" {
  description = "ECS が起動するバックエンドイメージの ECR タグ。APP_VERSION（JSON ログの version 属性）にも使う"
  type        = string
  default     = "latest"
}

variable "cpu_architecture" {
  description = "Fargate の CPU アーキテクチャ。ECR に push するイメージのアーキテクチャと一致させる（Apple Silicon で build するなら --platform を付けるか ARM64 にする）"
  type        = string
  default     = "X86_64"

  validation {
    condition     = contains(["X86_64", "ARM64"], var.cpu_architecture)
    error_message = "cpu_architecture は X86_64 か ARM64 のいずれか。"
  }
}

variable "task_cpu" {
  description = "Fargate タスクの CPU ユニット（1024 = 1 vCPU）"
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "Fargate タスクのメモリ（MiB）。Java 25 + Spring Boot は 1024 が現実的な下限"
  type        = number
  default     = 1024
}

variable "db_instance_class" {
  description = "RDS のインスタンスクラス"
  type        = string
  default     = "db.t4g.micro"
}

variable "log_retention_days" {
  description = "CloudWatch Logs の保持日数（docs/08_constraints.md TBD-15）"
  type        = number
  default     = 14
}
