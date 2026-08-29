# 12. ログ設計

> **前提:** 本ドキュメントは **アプリケーション（Spring Boot）側のログ設計** を定める。
> ログの収集・保管・検索を行う基盤（Datadog / CloudWatch Logs など）は現時点で存在せず、導入時期も未定（[08_constraints.md](08_constraints.md) TBD-15）。
> 基盤を入れる日に **環境変数の変更だけで接続できる** 状態にしておくことが本設計の目的であり、基盤固有の設定は「将来手順」として末尾にまとめる。

### 12.1 方針

| 方針 | 内容 | 理由 |
|------|------|------|
| 構造化ログ | 本番相当環境では 1 行 1 JSON で標準出力に書く | 収集基盤での検索・集計・アラートの前提。テキストを正規表現で切り出す運用は壊れやすい |
| 追加ライブラリなし | Spring Boot 4 標準の `logging.structured.format.console` を使う（logstash-logback-encoder 等は入れない） | 依存を増やさない。Boot のバージョンアップに追従するだけで済む |
| 形式は logstash 互換 | `@timestamp` / `level` / `logger_name` / `thread_name` / `message` / `stack_trace` | Datadog の Java 向け標準パイプラインがこの形式をそのまま標準属性へ変換する。ECS 形式より手戻りが少ない |
| 属性名は Datadog 標準属性 | MDC のキー名を `http.method` / `usr.id` / `duration` などに揃える | 取り込み側で facet / remap を作らなくても検索・集計できる。Datadog を使わなくても名前の意味は自明 |
| 出力先は標準出力のみ | ファイル出力・ローテーションはアプリでは行わない | コンテナ / systemd ではランタイム側がログを拾う。二重管理を避ける |
| 1 リクエスト 1 ID | すべてのログ行に `request_id` を付け、レスポンスヘッダ `X-Request-Id` で返す | 利用者の報告と、サーバー側の複数行のログを突き合わせるため |
| ID は書く・中身は書かない | ユーザー ID・投稿 ID は載せる。パスワード・トークン・メールアドレス・本文は載せない | 調査に必要なのは「誰が・何に」であって内容ではない。PII / 秘密情報の漏えい面を最小にする |

### 12.2 出力形式と切り替え

環境変数 `LOG_FORMAT` で切り替える（[application.properties](../backend/src/main/resources/application.properties) `logging.structured.format.console=${LOG_FORMAT:}`）。

| `LOG_FORMAT` | 形式 | 用途 |
|--------------|------|------|
| （空・未設定） | テキスト。`[rid=<request_id> uid=<usr.id>]` を行に含める | ローカル開発・テスト |
| `logstash` | 1 行 JSON | 本番・ステージング・ログ基盤への取り込み検証 |

**テキスト形式の例**

```
2026-08-22T14:56:20.123+09:00  INFO [nio-8080-exec-1] [rid=afeaecb8-0b95-4c81-bbf7-99db2f0e379f uid=7] c.e.m.service.PostService : 投稿を作成しました: postId=12, userId=7, images=1
2026-08-22T14:56:20.130+09:00  INFO [nio-8080-exec-1] [rid=afeaecb8-0b95-4c81-bbf7-99db2f0e379f uid=7] c.e.m.logging.RequestLoggingFilter : POST /api/posts -> 201 (118 ms)
```

**JSON 形式の例（アクセスログ 1 行）**

```json
{"@timestamp":"2026-08-22T14:59:32.336468+09:00","@version":"1","message":"POST /api/auth/signup -> 201 (959 ms)","logger_name":"com.example.mytimeline.logging.RequestLoggingFilter","thread_name":"http-nio-8080-exec-1","level":"INFO","level_value":20000,"duration":"959623041","http.status_code":"201","request_id":"afeaecb8-0b95-4c81-bbf7-99db2f0e379f","http.method":"POST","network.client.ip":"203.0.113.5","http.url_details.path":"/api/auth/signup","service":"mytimeline","env":"production","version":"0.0.1-SNAPSHOT"}
```

### 12.3 フィールド定義

#### 12.3.1 全行に付く項目

| フィールド | 由来 | 内容 | Datadog での扱い |
|-----------|------|------|------------------|
| `@timestamp` | Boot 標準 | ISO 8601（タイムゾーン付き） | 予約属性 `timestamp` |
| `level` / `level_value` | Boot 標準 | `ERROR` / `WARN` / `INFO` / `DEBUG` | 予約属性 `status` に remap される |
| `logger_name` | Boot 標準 | 出力元クラスの FQCN | `logger.name` |
| `thread_name` | Boot 標準 | スレッド名 | `logger.thread_name` |
| `message` | Boot 標準 | 人が読むメッセージ（日本語） | 予約属性 `message` |
| `stack_trace` | Boot 標準（例外付きログのみ） | 例外のスタックトレース | `error.stack` |
| `service` | `logging.structured.json.add.service` | `mytimeline`（`spring.application.name`） | 予約属性 `service` |
| `env` | `logging.structured.json.add.env` ← `APP_ENV` | `local` / `staging` / `production` | Unified Service Tagging の `env` |
| `version` | `logging.structured.json.add.version` ← `APP_VERSION` | デプロイしたビルドの版（未設定時 `unknown`） | Unified Service Tagging の `version` |

#### 12.3.2 リクエスト中のログに付く項目（MDC）

[RequestLoggingFilter](../backend/src/main/java/com/example/mytimeline/logging/RequestLoggingFilter.java) と [JwtAuthenticationFilter](../backend/src/main/java/com/example/mytimeline/security/JwtAuthenticationFilter.java) が MDC に載せる。リクエスト処理中に出るすべてのログ行（サービス層の `log.info` も含む）に自動で付く。

| フィールド | 設定箇所 | 内容 | 備考 |
|-----------|----------|------|------|
| `request_id` | RequestLoggingFilter | リクエスト ID。`X-Request-Id` ヘッダがあれば引き継ぎ、なければ UUID | レスポンスヘッダ `X-Request-Id` でも返す |
| `http.method` | RequestLoggingFilter | `GET` / `POST` … | Datadog 標準属性 |
| `http.url_details.path` | RequestLoggingFilter | リクエストパス（クエリ文字列は含めない） | 同上。検索語などが混ざらないようクエリは落とす |
| `http.status_code` | RequestLoggingFilter（完了時） | レスポンスのステータス | 同上 |
| `duration` | RequestLoggingFilter（完了時） | 所要時間 **ナノ秒** | Datadog の `duration` はナノ秒が規約。`message` にはミリ秒で併記 |
| `network.client.ip` | RequestLoggingFilter | `X-Forwarded-For` 先頭、なければ接続元 | 偽装可能なので記録用途のみ。認可には使わない |
| `usr.id` | JwtAuthenticationFilter（認証成功時） | ログイン中ユーザーの ID | `username` / `email` は載せない |

> **注記:** `/actuator/**` へのリクエストは MDC の設定・アクセスログの対象外（ヘルスチェックが数十秒ごとに来るため）。ただし MDC の後始末は行う。`shouldNotFilter` で素通しにすると、認証付きで `/actuator` を叩いたときに `usr.id` がスレッドに残り、次の無関係なリクエストのログに他人の ID が付く。

#### 12.3.3 リクエスト ID の伝搬

```
ブラウザ ──HTTP──▶ CloudFront ──▶ ALB ──▶ Spring Boot（RequestLoggingFilter）
                                              │ X-Request-Id があれば引き継ぎ / なければ UUID 採番
                                              │ MDC: request_id=...
                                              ▼
                                     Controller / Service / Mapper のログ行すべてに request_id が付く
                                              │
ブラウザ ◀── レスポンスヘッダ X-Request-Id ◀──┘
```

- 受け付ける `X-Request-Id` は `[A-Za-z0-9_-]{1,64}` のみ。改行や制御文字でログ行を偽造されないための制限。形式外の値は無視して採番し直す
- 利用者からの問い合わせでは **画面に出たエラー時刻と、可能なら DevTools の `X-Request-Id`** を聞く。ID があれば `request_id:<値>` で一発で該当リクエストの全ログに辿り着ける
- ALB や CloudFront を挟む場合、それらが付ける ID（`X-Amzn-Trace-Id` など）を `X-Request-Id` に転記する設定は **インフラ側の将来作業**（[08_constraints.md](08_constraints.md) TBD-15）

### 12.4 ログレベル基準

| レベル | 使う場面 | 例 | 監視での扱い |
|--------|----------|----|--------------|
| `ERROR` | アプリ側に原因がある失敗。人が見る必要がある | 5xx のアクセスログ、`GlobalExceptionHandler.handleUnexpected`、S3 への書き込み失敗 | 件数でアラート（[11_monitoring_design.md](11_monitoring_design.md) M-04） |
| `WARN` | 業務上は正しく拒否したが、頻発すると異常の兆候 | ログイン失敗、無効なリフレッシュトークン、他人の投稿への操作（403）、事前チェックをすり抜けた UNIQUE 違反 | 件数の急増でアラート（M-05） |
| `INFO` | 状態が変わった事実、リクエストの完了 | 投稿作成 / 削除、フォロー、アクセスログ | 通常は見ない。調査時に `request_id` で引く |
| `DEBUG` | 開発時のみ。本番では出さない | リクエスト解釈失敗の詳細、アップロードサイズ超過の詳細 | 障害調査で一時的に `LOG_LEVEL_APP=DEBUG` にする |

- 4xx のアクセスログは `INFO`。利用者の入力誤りや未ログインは正常系の一部で、件数はメトリクス（`http.server.requests`）で見る
- 同じ事象を複数レベルで二重に出さない（例: サービス層で `log.error` したうえで例外を投げ、ハンドラでも `log.error` しない）
- ログレベルは環境変数で変更できる: `LOG_LEVEL_ROOT`（既定 `INFO`）、`LOG_LEVEL_APP`（`com.example.mytimeline`、既定 `INFO`）

### 12.5 書いてはいけないもの

| 分類 | 具体例 | 代わりに書くもの |
|------|--------|------------------|
| 認証情報 | パスワード（平文・ハッシュとも）、JWT アクセストークン、リフレッシュトークン、`Authorization` ヘッダ、`Cookie` ヘッダ | 「失敗した」という事実と理由の種別のみ |
| 個人情報 | メールアドレス、表示名、`username`、`identifier`（ログイン ID） | `usr.id`（数値 ID） |
| 利用者が書いた内容 | 投稿本文、コメント本文、検索語（クエリ文字列） | `postId` / `commentId`、本文の文字数 |
| バイナリ | 画像の内容 | `s3_key`、サイズ、Content-Type |
| 内部情報をそのまま | SQL 文と実パラメータ、接続文字列 | 例外クラス名と `stack_trace`（`ERROR` のみ） |
| 環境変数 | `JWT_SECRET`、`S3_SECRET_KEY`、`DB_PASSWORD` | 「設定されている / いない」の事実のみ |

> **注記:** `GlobalExceptionHandler` が `stack_trace` を出すのは `handleUnexpected`（500）だけ。`MaxUploadSizeExceededException` や `HttpMessageNotReadableException` のメッセージには Spring 内部の情報が混ざるため `DEBUG` に留めている。

### 12.6 ログを書くときの規約（実装者向け）

- ロガーは `private static final Logger log = LoggerFactory.getLogger(X.class);`（Checkstyle が `log` を許容している）
- メッセージは日本語、値は `key={}` のプレースホルダで付ける: `log.info("投稿を作成しました: postId={}, userId={}", post.getId(), userId);`
- `request_id` / `usr.id` / `http.*` は **書かなくてよい**。MDC から自動で付く
- 例外は `log.error("...", e)` のように最後の引数で渡す（`e.getMessage()` だけ文字列連結しない。`stack_trace` が落ちる）
- ループの中で `INFO` を出さない（1 リクエスト 1〜数行を目安）
- 新しい MDC キーを足すときは Datadog 標準属性に既存の名前がないか先に確認する（`http.*`, `usr.*`, `network.*`, `error.*`, `db.*`）

### 12.7 動作確認手順

```bash
# 1. テキスト形式（既定）。rid / uid が行に出る
cd backend && ./gradlew bootRun
curl -i http://localhost:8080/api/timeline/all        # 401 でも X-Request-Id が返る

# 2. JSON 形式。1 行ずつ jq でパースできる
LOG_FORMAT=logstash APP_ENV=staging ./gradlew bootRun 2>&1 | grep '^{' | jq -c '{level, message, request_id, "usr.id", "http.status_code", duration}'

# 3. request_id で 1 リクエストの全行を引く
... | jq -c 'select(.request_id == "<X-Request-Id の値>")'

# 4. docker compose でも同じ（.env に LOG_FORMAT=logstash を書く）
docker compose logs -f backend | grep '^{' | jq -c '{level, message}'
```

自動テスト: [RequestLoggingFilterTest](../backend/src/test/java/com/example/mytimeline/logging/RequestLoggingFilterTest.java)（MDC・ログレベル・後始末）、[ObservabilityIntegrationTest](../backend/src/test/java/com/example/mytimeline/integration/ObservabilityIntegrationTest.java)（`X-Request-Id` の往復・Actuator の公開範囲）。JSON 形式そのものは、ログシステムの初期化が Spring コンテキストより前に行われテスト JVM 全体に影響するため自動テストにせず、上記 2. で確認する。

### 12.8 Datadog を導入するときの手順（将来・未実施）

> この節は **基盤導入時の作業メモ** であり、現時点では何も設定されていない。インフラ側の作業は [08_constraints.md](08_constraints.md) TBD-15。

| 手順 | 内容 | アプリ側の変更 |
|------|------|----------------|
| 1 | アプリを `LOG_FORMAT=logstash APP_ENV=production APP_VERSION=<ビルド版>` で起動 | なし（環境変数のみ） |
| 2 | Datadog Agent のログ収集を有効化し、コンテナ（または journald / ファイル）から標準出力を拾う。`source: java`, `service: mytimeline` を付ける | なし |
| 3 | `source:java` の標準パイプラインで `level → status`, `logger_name → logger.name`, `stack_trace → error.stack` が自動 remap されることを確認 | なし |
| 4 | `DD_SERVICE=mytimeline DD_ENV=production DD_VERSION=<版>` を Agent / アプリの環境変数に設定（本書の `service` / `env` / `version` と **同じ値** にする） | なし |
| 5 | （APM を使う場合）`dd-java-agent.jar` を `-javaagent` で付け、`DD_LOGS_INJECTION=true` を設定 → `dd.trace_id` / `dd.span_id` が MDC 経由で本書の JSON にそのまま乗る | なし |
| 6 | ログベースのモニターを [11_monitoring_design.md](11_monitoring_design.md) の閾値で作成 | なし |

Datadog 以外（CloudWatch Logs + Logs Insights、Loki、OpenSearch）でも、1 行 JSON であれば同じフィールド名で検索できる。`@timestamp` / `level` のようにツール側の既定名と違う場合は取り込み側で remap する。

### 12.9 未決事項

| 項目 | 状況 |
|------|------|
| ログの保持期間 | 基盤未導入のため未決。一般的には本番 30 日（検索可能）＋ 1 年（アーカイブ）を目安にする |
| `X-Request-Id` を ALB / CloudFront から付与するか | 未決。アプリ側はどちらでも動く（受け取れば引き継ぎ、なければ採番） |
| アクセスログをアプリで出し続けるか、ALB アクセスログに寄せるか | 未決。ALB ログには `usr.id` が無いため、当面はアプリ側で出す |
