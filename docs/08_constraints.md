# 9. 制約・前提条件

| 項目 | 内容 |
|------|------|
| 動作環境 | Google Chrome 最新版（デスクトップを主対象） |
| ネットワーク | バックエンド API への接続が必要 |
| 認証 | 認証が必要な操作にはログインが必須 |
| 画像 | 投稿画像・プロフィール画像とも AWS S3 に保存し、JPEG / PNG・2MB・4096px 以内。投稿画像は 1 投稿 4 枚まで（[F03_post.md](features/F03_post.md) / [F07_profile.md](features/F07_profile.md)）。ローカル開発では S3 互換の MinIO を使う |
| データ量 | 投稿・コメント・いいね数に上限は設けない（初期フェーズ） |
| 本番環境 | AWS（CloudFront + S3 + ALB + ECS Fargate + RDS）。EC2 なし。学習用途のため使わないときは destroy する（[09_infrastructure.md](09_infrastructure.md)） |

---

## 10. 未決事項

| ID | 項目 | 内容 | ステータス |
|----|------|------|-----------|
| TBD-01 | 認証方式の詳細 | **JWT（HS256）方式に決定。** Spring Security をステートレス構成にし、`Authorization: Bearer <token>` で認証する。詳細は [F01_auth.md](features/F01_auth.md) | 決定済み |
| TBD-02 | AWS サーバ構築の可否 | **EC2 なし（ECS Fargate）で構築することに決定。** 常時稼働はせず、学習時に `terraform apply`、終わったら `destroy` する運用（[09_infrastructure.md](09_infrastructure.md) 11.6） | 決定済み |
| TBD-03 | IaC / デプロイ手順 | **IaC 化済み。** VPC / ALB / ECS Fargate / ECR / RDS / CloudFront / S3 / IAM / SSM を `terraform/` で定義し、構築 → イメージ push → フロント配置 → destroy の手順を [terraform/README.md](../terraform/README.md) に記載。CI/CD からの自動デプロイは未着手 | 決定済み（自動デプロイは未決） |
| TBD-04 | 投稿本文の上限文字数 | 暫定 280 文字。正式値は機能定義書で確定する | 未決 |
| TBD-05 | 画像枚数・形式・サイズ上限 | **実装済みに決定。** 最大 4 枚 / JPEG・PNG / 1 枚あたり 2MB・縦横 4096px 以内（プロフィール画像と同じ検証を共用。[F03_post.md](features/F03_post.md) 6.） | 決定済み |
| TBD-06 | プロフィール編集機能 | **実装済みに決定。** 表示名・自己紹介・プロフィール画像を編集可能とする。username とメールアドレスは変更不可（前者は参照先の URL、後者はログイン手段のため別手続き）。詳細は [F07_profile.md](features/F07_profile.md) | 決定済み |
| TBD-07 | TanStack Query の導入可否 | [02_tech_stack.md](02_tech_stack.md) に記載していたが未導入。現状はサーバー状態を自前フック（`frontend/src/hooks/useTimeline.ts`）で保持している。導入するかは今後判断する | 未決 |
| TBD-08 | 日時のタイムゾーン | DB は `TIMESTAMP`（タイムゾーンなし）、API は `LocalDateTime` をタイムゾーン無しの文字列で返す。**正しさをコンテナの `TZ=Asia/Tokyo`（docker-compose.yml）に依存している**。複数リージョン運用や、サーバーとブラウザのタイムゾーンが異なる状況では `TIMESTAMPTZ` + `Instant`/`OffsetDateTime` への移行が必要 | 未決（学習段階では現状で許容） |
| TBD-09 | 画像 URL のキャッシュ効率 | 期限付きの署名付き URL を毎回発行しているため、同じ画像（プロフィール・投稿とも）でもブラウザキャッシュが効かず再取得になる。CloudFront + 署名付き Cookie / OAC に移行して URL を安定させるかは今後判断する（[F07_profile.md](features/F07_profile.md) 5.）。現状の本番構成でも画像は CloudFront を通さず S3 の署名付き URL のまま。サーバー側で毎回署名を計算するコストは [13_performance_test.md](13_performance_test.md) の focus（`limit=20` と `50` の対）で計測できる | 未決 |
| TBD-10 | アップロード画像の EXIF | アップロードされた画像のメタデータ（撮影日時・GPS 座標など）を除去していない。プロフィール画像・投稿画像は第三者に公開されるため、位置情報が意図せず共有されうる。除去処理の追加を検討する | 未決 |
| TBD-11 | 参照されない画像の回収 | ストレージ操作はトランザクションでロールバックできないため、プロフィール画像の差し替えや画像付き投稿の作成が途中で失敗すると孤児オブジェクトが残りうる。定期的な掃除の要否は今後判断する（[F07_profile.md](features/F07_profile.md) 6. / [F03_post.md](features/F03_post.md) 7.） | 未決 |
| TBD-12 | ユーザー検索のスケーラビリティ | 部分一致を `ILIKE '%...%'` で実装しているため、前方一致と違いインデックスが効かず `users` を全件走査する。ユーザー数が増えたら pg_trgm の GIN インデックスか全文検索への移行が必要（[F06_follow.md](features/F06_follow.md) 5.）。現在のデータ量での応答時間は [13_performance_test.md](13_performance_test.md) の focus（ヒットあり / なしの対）で計測できる | 未決（学習段階では現状で許容） |
| TBD-13 | フォロー中タイムラインの絞り込み方 | フォロー先の id を `IN` で渡しているため、フォロー数が数千規模になるとクエリが肥大する。`follows` を JOIN する形への移行が必要になるかは今後判断する（[F06_follow.md](features/F06_follow.md) 5.）。フォロー数による差は [13_performance_test.md](13_performance_test.md) の focus（約 50 人と約 1,000 人の対）で計測できる | 未決 |
| TBD-15 | ログ基盤・保持期間 | **本番では CloudWatch Logs に決定。** ECS の awslogs ドライバで `/ecs/mytimeline` に JSON ログを送り、14 日保持（`terraform/ecs.tf`）。Datadog 等への転送と、ALB / CloudFront から `X-Request-Id` を付与するかは未決 | 一部決定 |
| TBD-16 | メトリクスエンドポイントの公開方式 | `/actuator/prometheus` は現状 JWT 認証必須で、監視エージェントからは取得できない。管理ポート分離（`management.server.port`）＋セキュリティグループ制限か、dd-java-agent（JMX / APM）経由かを監視基盤導入時に決める（[11_monitoring_design.md](11_monitoring_design.md) 13.2） | 未決 |
| TBD-17 | ログイン試行のレート制限 | アプリ側にレート制限が無く、ブルートフォースは監視（M-14 / M-15）で検知してインフラ側（WAF / SG）で遮断する前提。アプリ側での制限（IP / アカウント単位のロック）を入れるかは未決（[12_incident_response.md](12_incident_response.md) RB-07） | 未決 |
| TBD-14 | 予約された username | `/api/users/search` が `/api/users/{username}` より優先されるため、`search` という username のプロフィールは開けない。登録時に予約語として弾くかは今後判断する（[F06_follow.md](features/F06_follow.md) 4.） | 未決 |
