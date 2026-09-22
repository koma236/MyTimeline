terraform {
  required_version = "~> 1.15"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    # DB パスワード・JWT 秘密鍵・オリジン検証ヘッダの値を生成する。
    # 人が値を決めて tfvars に書く運用にすると、その tfvars が秘密情報になってしまう
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}
