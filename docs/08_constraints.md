# 9. 制約・前提条件

| 項目 | 内容 |
|------|------|
| 動作環境 | Google Chrome 最新版（デスクトップを主対象） |
| ネットワーク | バックエンド API への接続が必要 |
| 認証 | 認証が必要な操作にはログインが必須 |
| 画像 | 投稿画像は AWS S3 に保存。対応形式・サイズ上限は機能定義書（[F03_post.md](features/F03_post.md)）で規定。アバター画像は JPEG / PNG・2MB・4096px 以内（[F07_profile.md](features/F07_profile.md)）。ローカル開発では S3 互換の MinIO を使う |
| データ量 | 投稿・コメント・いいね数に上限は設けない（初期フェーズ） |
| 本番環境 | AWS でのサーバ構築は**未確定**。構築する場合は ALB + EC2 + RDS + S3 を前提（[09_infrastructure.md](09_infrastructure.md)） |

---

## 10. 未決事項

| ID | 項目 | 内容 | ステータス |
|----|------|------|-----------|
| TBD-01 | 認証方式の詳細 | **JWT（HS256）方式に決定。** Spring Security をステートレス構成にし、`Authorization: Bearer <token>` で認証する。詳細は [F01_auth.md](features/F01_auth.md) | 決定済み |
| TBD-02 | AWS サーバ構築の可否 | サーバ（EC2/RDS/ALB）を実際に構築するかは未確定。構築時は [09_infrastructure.md](09_infrastructure.md) の構成を前提とする | 未決 |
| TBD-03 | IaC / デプロイ手順 | Terraform 等の IaC・詳細なデプロイ手順書は今回のドキュメント対象外。今後検討 | 未決 |
| TBD-04 | 投稿本文の上限文字数 | 暫定 280 文字。正式値は機能定義書で確定する | 未決 |
| TBD-05 | 画像枚数・形式・サイズ上限 | 暫定「最大 4 枚 / JPEG・PNG / 1 枚あたり数 MB」。正式値は [F03_post.md](features/F03_post.md) で確定する | 未決 |
| TBD-06 | プロフィール編集機能 | **実装済みに決定。** 表示名・自己紹介・アバター画像を編集可能とする。username とメールアドレスは変更不可（前者は参照先の URL、後者はログイン手段のため別手続き）。詳細は [F07_profile.md](features/F07_profile.md) | 決定済み |
| TBD-07 | TanStack Query の導入可否 | [02_tech_stack.md](02_tech_stack.md) に記載していたが未導入。現状はサーバー状態を自前フック（`frontend/src/hooks/useTimeline.ts`）で保持している。導入するかは今後判断する | 未決 |
| TBD-08 | 日時のタイムゾーン | DB は `TIMESTAMP`（タイムゾーンなし）、API は `LocalDateTime` をタイムゾーン無しの文字列で返す。**正しさをコンテナの `TZ=Asia/Tokyo`（docker-compose.yml）に依存している**。複数リージョン運用や、サーバーとブラウザのタイムゾーンが異なる状況では `TIMESTAMPTZ` + `Instant`/`OffsetDateTime` への移行が必要 | 未決（学習段階では現状で許容） |
| TBD-09 | アバター画像のキャッシュ効率 | 期限付きの署名付き URL を毎回発行しているため、同じ画像でもブラウザキャッシュが効かず再取得になる。CloudFront + 署名付き Cookie / OAC に移行して URL を安定させるかは今後判断する（[F07_profile.md](features/F07_profile.md) 5.） | 未決 |
| TBD-10 | アップロード画像の EXIF | アップロードされた画像のメタデータ（撮影日時・GPS 座標など）を除去していない。アバターは第三者に公開されるため、位置情報が意図せず共有されうる。除去処理の追加を検討する | 未決 |
| TBD-11 | 参照されない画像の回収 | アバター差し替え時に古い画像を削除しているが、ストレージ操作はトランザクションでロールバックできないため、失敗時に孤児オブジェクトが残りうる。定期的な掃除の要否は今後判断する（[F07_profile.md](features/F07_profile.md) 6.） | 未決 |
