package com.example.mytimeline.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * リクエスト 1 件ごとの識別情報を MDC に載せ、完了時にアクセスログを 1 行出すフィルタ。
 *
 * <p>{@code security.JwtAuthenticationFilter} と違い、こちらは Spring Security のチェーンの
 * <b>外側</b>（サーブレットフィルタとして最優先）で動かすことを意図している。
 * セキュリティチェーンの中に置くと、認可で弾かれた 401 / 403 がアクセスログに残らない。
 * 登録は {@code config.LoggingConfig} の {@code FilterRegistrationBean} で行う。</p>
 *
 * <p>MDC のキー名は Datadog の標準属性（Standard Attributes）に揃えている。
 * JSON ログをそのまま取り込んだときに facet 設定なしで検索・集計できるようにするためで、
 * Datadog を使わない場合でも名前の意味は自明なので困らない。
 * 詳細は docs/10_logging_design.md を参照。</p>
 *
 * <p>ログイン中のユーザー ID（{@code usr.id}）はこのクラスでは載せられない。SecurityContext は
 * セキュリティチェーンを抜けるときに消されるため、チェーン内の {@code JwtAuthenticationFilter} が
 * 認証成功時に MDC へ書き込む。MDC の後始末（clear）はこのクラスが一括で行う。</p>
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    /** 外部（ALB / CloudFront / クライアント）から引き継ぐリクエスト ID のヘッダ名。 */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    public static final String MDC_REQUEST_ID = "request_id";
    public static final String MDC_HTTP_METHOD = "http.method";
    public static final String MDC_HTTP_PATH = "http.url_details.path";
    public static final String MDC_HTTP_STATUS = "http.status_code";
    public static final String MDC_CLIENT_IP = "network.client.ip";
    /** Datadog の {@code duration} 属性はナノ秒が規約。 */
    public static final String MDC_DURATION = "duration";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /**
     * 外部から受け取るリクエスト ID の許容形式。ログに埋め込む値なので、改行や制御文字で
     * ログ行を偽造されないよう英数字・ハイフン・アンダースコアに限定する。
     * 長さの上限 64 は UUID（36 文字）と ALB のトレース ID（約 40 文字）が収まる程度の余裕。
     */
    static final int REQUEST_ID_MAX_LENGTH = 64;
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1," + REQUEST_ID_MAX_LENGTH + "}$");

    private static final int SERVER_ERROR_THRESHOLD = 500;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        // ヘルスチェック / メトリクス収集は数十秒ごとに叩かれ、アクセスログの大半を占めてしまう。
        // 異常はヘルスチェック側（監視）で検知するので、アプリのログには残さない。
        // ただし shouldNotFilter で丸ごと素通しにはしない。認証付きで /actuator を叩かれると
        // チェーン内の JwtAuthenticationFilter が usr.id を MDC に書き、誰も消さないまま
        // スレッドが次のリクエストに使い回され、無関係なログに他人の ID が付いてしまう
        if (request.getRequestURI().startsWith("/actuator")) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                MDC.clear();
            }
            return;
        }

        long startNanos = System.nanoTime();
        String requestId = resolveRequestId(request);

        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_HTTP_METHOD, request.getMethod());
        MDC.put(MDC_HTTP_PATH, request.getRequestURI());
        MDC.put(MDC_CLIENT_IP, resolveClientIp(request));
        // 利用者がサポートに問い合わせるときの手掛かりとして返す。
        // ブラウザの DevTools や curl -i でそのまま見える
        response.setHeader(REQUEST_ID_HEADER, requestId);

        // チェーンから例外が抜けてきた場合、レスポンスのステータスはまだ 200 のままのことがある。
        // その状態でアクセスログを出すと「成功」に見えてしまうので、例外時は 500 として記録する
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            filterChain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            long elapsedNanos = System.nanoTime() - startNanos;
            MDC.put(MDC_HTTP_STATUS, Integer.toString(status));
            MDC.put(MDC_DURATION, Long.toString(elapsedNanos));
            logAccess(request, status, elapsedNanos);
            // ThreadLocal なので、スレッドプールで使い回される前に必ず消す
            MDC.clear();
        }
    }

    private void logAccess(HttpServletRequest request, int status, long elapsedNanos) {
        long elapsedMillis = elapsedNanos / NANOS_PER_MILLI;
        if (status >= SERVER_ERROR_THRESHOLD) {
            // 5xx は「アプリ側の障害」なので ERROR。監視の ERROR ログ件数アラートに乗せる
            log.error("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), status, elapsedMillis);
        } else {
            // 4xx は利用者側の誤り・認可拒否で正常系の一部。件数はメトリクス（http.server.requests）で見る
            log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), status, elapsedMillis);
        }
    }

    /**
     * 外部から来た ID が形式を満たしていればそれを引き継ぎ、それ以外は新規採番する。
     * ALB / CloudFront を挟んだ構成で、経路全体を 1 つの ID で追えるようにするため。
     */
    static String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming != null && REQUEST_ID_PATTERN.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * ロードバランサ越しでは {@code getRemoteAddr()} が LB のアドレスになるため、
     * {@code X-Forwarded-For} の先頭（最初に経由した＝クライアントに最も近い）を優先する。
     * ヘッダはクライアントが偽装できるので、認可には使わず記録用途に留めること。
     */
    static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
