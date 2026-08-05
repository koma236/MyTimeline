# 認証情報はここに書かず、環境変数または ~/.aws/credentials に委ねる
provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# バケット名のグローバル一意化に使うアカウント ID
data "aws_caller_identity" "current" {}
