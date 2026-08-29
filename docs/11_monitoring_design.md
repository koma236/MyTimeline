# 13. 監視運用設計

> **前提:** 本ドキュメントは **アプリケーション層（Spring Boot）の監視項目と閾値** を定める。
> 監視基盤（Datadog / CloudWatch など）は現時点で存在せず、EC2 / RDS / ALB のインフラ層監視（CPU・ディスク・RDS 接続数・ALB ヘルス）も [09_infrastructure.md](09_infrastructure.md) の構築とあわせて別途設計する。
> 閾値は **一般的な Web アプリケーションの目安** であり、本番トラフィックが分かった時点で見直す（[13.6](#136-閾値の見直し)）。

### 13.1 監視の考え方

| 観点 | 何を見るか | 取得元 |
|------|-----------|--------|
| 死活（生きているか） | プロセスが応答するか、依存先（DB）に繋がるか | `/actuator/health/liveness`, `/actuator/health/readiness` |
| RED（利用者から見た品質） | **R**ate: リクエスト数 / **E**rrors: 5xx 率 / **D**uration: レイテンシ p95・p99 | `/actuator/prometheus` の `http_server_requests_seconds_*` |
| 飽和（資源が足りているか） | JVM ヒープ、GC、DB コネクションプール、Tomcat スレッド | `/actuator/prometheus` の `jvm_*`, `hikaricp_*`, `tomcat_threads_*` |
| 業務の兆候 | ログイン失敗の急増、認可拒否の急増、S3 失敗 | JSON ログ（[10_logging_design.md](10_logging_design.md)） |

**アラートの原則**

- **Critical は「利用者に影響が出ている、または数分以内に出る」ものだけ。** それ以外は Warning
- Critical は人を呼ぶ（オンコール通知）。Warning はチャット通知に留め、営業時間内に確認する
- 同じ原因で複数のアラートが鳴る場合（DB 断 → readiness DOWN・5xx 率・ERROR ログ）は、**根本に近い方（readiness）を Critical、派生を Warning** にして通知を絞る
- すべてのアラートに対応手順（[12_incident_response.md](12_incident_response.md) の Runbook）を 1 対 1 で用意する。手順の無いアラートは作らない

### 13.2 取得元（アプリが公開しているもの）

| エンドポイント | 認証 | 内容 | 備考 |
|---------------|------|------|------|
| `GET /actuator/health` | 不要 | `{"status":"UP"}` / `DOWN`。依存先の内訳（components）は出さない | 内部構成を外に出さないため `show-details=never` |
| `GET /actuator/health/liveness` | 不要 | プロセスが生きているか。DB 断でも UP | コンテナ・systemd の再起動判定に使う。DB 断で再起動させない |
| `GET /actuator/health/readiness` | 不要 | リクエストを受け付けられるか。DB（DataSource）断で DOWN | **ALB のヘルスチェックはこちら**。DOWN なら振り分けから外れる |
| `GET /actuator/prometheus` | **必要** | Prometheus テキスト形式のメトリクス | Datadog Agent の OpenMetrics チェック / Prometheus のどちらでも取れる |
| `GET /actuator/metrics/{name}` | **必要** | 個別メトリクスの JSON | 手動調査用 |
| `GET /actuator/info` | **必要** | ビルド情報（version / time） | 「どのビルドが動いているか」の確認 |

> **注記（監視エージェントからの取得）:** `prometheus` / `metrics` / `info` は現状 JWT 認証が必要で、エージェントはログインできない。導入時は次のどちらかを採る（[08_constraints.md](08_constraints.md) TBD-16）。
> (a) `management.server.port=8081` で管理ポートを分け、セキュリティグループで Agent からのみ到達可能にしたうえで `/actuator/**` を permitAll にする（推奨）。
> (b) dd-java-agent の JMX 連携で JVM メトリクスを取り、HTTP 系は APM のトレースから得る（HTTP エンドポイントを公開しない）。

### 13.3 監視項目と閾値

評価窓は原則 5 分。`Runbook` 列は [12_incident_response.md](12_incident_response.md) の該当節。

#### 13.3.1 死活・可用性

| ID | 項目 | 指標 | Warning | Critical | 根拠 | Runbook |
|----|------|------|---------|----------|------|---------|
| M-01 | readiness | `GET /actuator/health/readiness` が 200 以外 | 連続 2 回失敗（30 秒間隔） | 連続 3 回失敗（= 約 90 秒） | ALB が振り分けから外す条件と揃える。単発失敗は GC 停止等で起こりうる | RB-01 |
| M-02 | liveness | `GET /actuator/health/liveness` が 200 以外 | — | 連続 3 回失敗 | プロセスハング。自動再起動の対象 | RB-01 |

#### 13.3.2 RED（利用者から見た品質）

| ID | 項目 | 指標（Prometheus 名） | Warning | Critical | 根拠 | Runbook |
|----|------|-----------------------|---------|----------|------|---------|
| M-03 | 5xx 率 | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))` | 1 % | 5 % | 1 % は「ごく一部の利用者が失敗している」、5 % は「多くの利用者が失敗している」目安。リクエスト数が 5 分で 20 件未満のときは評価しない（1 件の失敗で 5 % を超える） | RB-02 |
| M-04 | ERROR ログ件数 | JSON ログ `level:ERROR` の件数 / 5 分 | 1 件以上 | 10 件以上 | アプリは ERROR を「人が見るべきもの」に限定している（[10_logging_design.md](10_logging_design.md) 12.4）ので 1 件でも通知する | RB-02 |
| M-05 | レイテンシ p95 | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{uri!~"/actuator.*"}[5m])) by (le))` | 1 秒 | — | [05_nonfunctional.md](05_nonfunctional.md)「通常操作は 1 秒以内」 | RB-03 |
| M-06 | レイテンシ p99 | 同上 `0.99` | 3 秒 | 5 秒 | p99 はタイムライン取得（JOIN・画像 URL 署名）のような重い操作を含むため p95 より緩く取る | RB-03 |
| M-07 | リクエスト数の急減 | `sum(rate(http_server_requests_seconds_count[5m]))` が直前 1 時間の同時間帯平均の 20 % 未満 | 営業時間内のみ | — | 「エラーは出ていないが誰も来られない」（DNS・CloudFront・フロント配信の障害）を検知する | RB-04 |

#### 13.3.3 飽和（資源）

| ID | 項目 | 指標（Prometheus 名） | Warning | Critical | 根拠 | Runbook |
|----|------|-----------------------|---------|----------|------|---------|
| M-08 | JVM ヒープ使用率 | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}`（10 分継続） | 80 % | 90 % | GC 後も戻らない高止まりはリーク。瞬間値は GC 前に高くなるので継続条件を付ける | RB-05 |
| M-09 | GC 停止時間 | `rate(jvm_gc_pause_seconds_sum[1m])` | 1 秒 / 分 | 5 秒 / 分 | 1 分のうち 5 秒止まっていればレイテンシに直結 | RB-05 |
| M-10 | DB コネクション待ち | `hikaricp_connections_pending > 0` が 1 分継続 | 発生 | 5 以上が 1 分継続 | プール枯渇。既定プール 10 に対し待ちが出た時点で遅延が始まる | RB-06 |
| M-11 | DB コネクション使用率 | `hikaricp_connections_active / hikaricp_connections_max` | 80 % | — | M-10 の前兆 | RB-06 |
| M-12 | DB 接続取得の失敗 | `rate(hikaricp_connections_timeout_total[5m]) > 0` | — | 発生 | 30 秒待っても取れなかった。ほぼ DB 障害 | RB-06 |
| M-13 | Tomcat スレッド | `tomcat_threads_busy_threads / tomcat_threads_config_max_threads` | 80 % | 95 % | 既定 200。上限に張り付くとリクエストがキューに溜まる | RB-03 |

#### 13.3.4 業務の兆候（ログベース）

| ID | 項目 | 指標（ログクエリ） | Warning | Critical | 根拠 | Runbook |
|----|------|-------------------|---------|----------|------|---------|
| M-14 | ログイン失敗の急増 | `http.url_details.path:/api/auth/login http.status_code:401` の件数 / 1 分 | 30 件 | 100 件 | ブルートフォース / クレデンシャルスタッフィングの兆候。平常時は数件 | RB-07 |
| M-15 | 同一 IP からの 401 | 同上を `network.client.ip` で group by、上位 1 IP | 20 件 / 分 | 50 件 / 分 | 単一の攻撃元の特定 | RB-07 |
| M-16 | 認可拒否（403）の急増 | `http.status_code:403` 件数 / 5 分 | 20 件 | — | 他人の投稿への操作を繰り返している（ID の総当たり）兆候 | RB-07 |
| M-17 | S3 操作の失敗 | `logger_name:com.example.mytimeline.storage.S3StorageService level:ERROR` / 5 分 | 1 件 | 5 件 | 画像投稿・アバター更新が失敗する。IAM / バケット / ネットワークの問題 | RB-08 |
| M-18 | ログイン成功率 | `/api/auth/login` の 200 ÷ 全件（5 分、10 件以上のとき） | 80 % 未満 | 50 % 未満 | 失敗が多いのは攻撃か、パスワード照合（BCrypt）/ DB の異常 | RB-07 |
| M-19 | 想定外例外 | `logger_name:com.example.mytimeline.exception.GlobalExceptionHandler level:ERROR` | 1 件 | 10 件 / 5 分 | `handleUnexpected` に落ちた＝未知の不具合 | RB-02 |

### 13.4 Datadog でのクエリ例（導入時の雛形）

モニターを作るときにそのまま貼れる形で残す。メトリクス名は Datadog の OpenMetrics チェックで取り込んだ場合（`_seconds` → `.seconds` のように `.` 区切りになる）。

| ID | 種別 | クエリ |
|----|------|--------|
| M-03 | Metric | `sum:http.server.requests.seconds.count{service:mytimeline,status:5*}.as_rate() / sum:http.server.requests.seconds.count{service:mytimeline}.as_rate() > 0.05` |
| M-04 | Log | `service:mytimeline status:error` → count > 10 (last 5m) |
| M-05 | Metric | `p95:http.server.requests.seconds{service:mytimeline,!uri:/actuator*} > 1` |
| M-08 | Metric | `avg(last_10m):jvm.memory.used.bytes{area:heap} / jvm.memory.max.bytes{area:heap} > 0.9` |
| M-10 | Metric | `min(last_1m):hikaricp.connections.pending{service:mytimeline} > 0` |
| M-14 | Log | `service:mytimeline @http.url_details.path:/api/auth/login @http.status_code:401` → count > 100 (last 1m) |
| M-15 | Log | 同上を `group by @network.client.ip` → any group > 50 |
| M-17 | Log | `service:mytimeline @logger.name:com.example.mytimeline.storage.S3StorageService status:error` |
| 調査 | Log | `service:mytimeline @request_id:<X-Request-Id>`（1 リクエストの全行） |
| 調査 | Log | `service:mytimeline @usr.id:<ユーザー ID>`（1 利用者の全操作） |

### 13.5 通知設計

| 重大度 | 通知先 | 期待する初動 | 再通知 |
|--------|--------|--------------|--------|
| Critical | オンコール（電話 / プッシュ）＋ 障害チャンネル | 15 分以内に確認開始（[12_incident_response.md](12_incident_response.md) 14.2） | 30 分ごと（未確認の間） |
| Warning | 監視チャンネル（チャット） | 営業時間内に確認。3 日以内に原因を記録 | なし |

- **復旧通知**も必ず送る（Critical が閾値を下回ったら「回復」を同じチャンネルへ）
- デプロイ直後 5 分間は M-05 / M-06 / M-08（ウォームアップで悪化する項目）を **抑制** する
- 同じモニターは 1 時間に 1 回までに **まとめる**（フラッピング対策）
- 閾値は `warning` → `critical` の順に必ず段階を付け、いきなり人を呼ばない

### 13.6 ダッシュボード構成案

1 画面で「今壊れているか」が分かる順に並べる。

| 段 | ウィジェット | 指標 |
|----|-------------|------|
| 1 | 死活・エラー | readiness の UP/DOWN、5xx 率（M-03）、ERROR ログ件数（M-04） |
| 2 | RED | リクエスト数 / 分（uri 別上位 10）、p50 / p95 / p99（M-05, M-06） |
| 3 | 認証 | ログイン件数・成功率（M-18）、401 件数（M-14） |
| 4 | JVM | ヒープ使用率（M-08）、GC 停止時間（M-09）、スレッド数 |
| 5 | DB | HikariCP active / idle / pending（M-10, M-11）、接続取得時間 |
| 6 | 外部 | S3 エラー（M-17）、S3 操作のログ件数 |
| 7 | デプロイ | `version` ごとのリクエスト数（デプロイ境界を線で出す） |

### 13.7 閾値の見直し

- 本番開始後 **2 週間** の実測（p95 / p99、5xx 率、JVM ヒープの定常値）で初回見直しを行う
- 見直しのルール: Warning は「月に数回鳴る」、Critical は「鳴ったら本当に障害」になるように調整する。1 週間に 3 回以上 Critical が空振りしたら閾値か評価窓を緩める
- 変更は本ドキュメントの表を更新し、PR で残す

### 13.8 未実施事項（インフラ側・将来）

| 項目 | 状況 |
|------|------|
| 監視エージェントの配置と `/actuator/prometheus` の公開方式 | 未決（[08_constraints.md](08_constraints.md) TBD-16） |
| ALB ヘルスチェックのパスを `/actuator/health/readiness` にする | [09_infrastructure.md](09_infrastructure.md) 構築時に設定 |
| 外形監視（Synthetics: ログイン → タイムライン取得） | 未決。M-07 の代替として有効 |
| インフラ層（EC2 CPU / ディスク、RDS CPU / 接続数 / ストレージ、S3 4xx/5xx） | インフラ構築時に別途設計 |
| フロントエンド（CloudFront 5xx、JS エラー収集） | 対象外（本書はバックエンドのみ） |
