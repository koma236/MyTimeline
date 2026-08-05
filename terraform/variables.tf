variable "project" {
  description = "リソース名・タグに使うプロジェクト名"
  type        = string
  default     = "mytimeline"
}

variable "environment" {
  description = "環境名（タグ用）"
  type        = string
  default     = "prod"
}

variable "region" {
  description = "AWS リージョン。アプリの S3_REGION と一致させる"
  type        = string
  default     = "ap-northeast-1"
}
