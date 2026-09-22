# 6. 非機能要件

| 項目 | 要件 |
|------|------|
| アーキテクチャ | SPA（シングルページアプリケーション）＋ REST API バックエンド |
| レスポンシブ | デスクトップを主対象（モバイル最適化は将来対応） |
| ブラウザ対応 | Google Chrome 最新版 |
| アクセシビリティ | 主要 7 画面で axe-core（WCAG 2.1 AA）の serious / critical 違反が無いことを E2E で検査する（[14_e2e_test.md](14_e2e_test.md) 16.5・16.8） |
| パフォーマンス | 通常操作（投稿・いいね・コメント等）は 1 秒以内に反映。タイムラインはページング（無限スクロール想定）で初期表示を高速化。達成状況は k6 による負荷テスト（サーバー側・[13_performance_test.md](13_performance_test.md)）と Playwright によるブラウザ側の計測（操作の反映時間・FCP / LCP / CLS・[14_e2e_test.md](14_e2e_test.md)）で任意のタイミングに確かめる |
| 認証・認可 | Spring Security によるトークン（またはセッション）認証。認証が必要な API は未ログインで拒否。投稿・コメントの削除は本人のみ許可（認可チェック） |
| セキュリティ | HTTPS 通信必須。パスワードはハッシュ化して保存（平文保存しない）。フロント・バック双方で入力値バリデーションを実施。画像は許可された形式・サイズのみ受け付ける |
| 画像ストレージ | 投稿画像は AWS S3 に保存。DB には `s3_key` のみ保持し、配信は CloudFront 経由を想定 |
| エラーハンドリング | API リクエスト失敗時はユーザーにエラーメッセージを表示する。バリデーションエラーは項目単位で表示する |
| 保守性 | ソースコードは Git で管理する。DB スキーマは Flyway でマイグレーション管理する |
| 可用性 | 学習用のため厳密な SLA は設けない。ECS Fargate は 1 タスク運用。将来的にタスク数の増加と RDS の Multi-AZ 化で可用性向上を検討（[09_infrastructure.md](09_infrastructure.md)） |
| バックアップ | RDS の自動バックアップ（1 日保持）。destroy でデータは消えるため、残したいときは事前にスナップショットを取る（[terraform/README.md](../terraform/README.md)） |
| ログ | 本番相当環境では 1 行 JSON の構造化ログを標準出力に書く。全行に `request_id` / ログイン中の `usr.id` を付け、レスポンスヘッダ `X-Request-Id` で利用者に返す。パスワード・トークン・メールアドレス・本文は記録しない（[10_logging_design.md](10_logging_design.md)） |
| 監視 | `/actuator/health/{liveness,readiness}` と `/actuator/prometheus` を公開し、5xx 率 1 % / 5 %、p95 1 秒、JVM ヒープ 80 % / 90 % などの閾値で監視する。監視基盤の導入は未決（[11_monitoring_design.md](11_monitoring_design.md)） |
| 障害対応 | SEV1〜3 の重大度と、検知 → 切り分け → 暫定対処 → 復旧 → ポストモーテムのフローを定める。アラートごとに Runbook を持つ（[12_incident_response.md](12_incident_response.md)） |
