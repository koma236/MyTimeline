package com.example.mytimeline.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link RequestLoggingFilter} の MDC とアクセスログを検証する。
 *
 * <p>設計技法:
 * <ul>
 *   <li>同値分割 — X-Request-Id ヘッダ「あり（正しい形式）」「なし」「不正な形式」</li>
 *   <li>境界値分析 — ヘッダ長 64（上限）/ 65（超過）</li>
 *   <li>分岐網羅 — ステータス 2xx / 4xx → INFO、5xx → ERROR、/actuator → ログなし</li>
 *   <li>状態遷移 — チェーンが例外で抜けても MDC が clear され、500 として記録される</li>
 * </ul></p>
 */
class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;
    private MockHttpServletResponse response;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
        response = new MockHttpServletResponse();
        logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    /** チェーンの中で MDC を観測するため、到達時点のスナップショットを返すチェーン。 */
    private static final class CapturingChain extends MockFilterChain {
        private java.util.Map<String, String> mdcAtChain;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            mdcAtChain = MDC.getCopyOfContextMap();
            ((MockHttpServletResponse) response).setStatus(200);
        }
    }

    @Test
    @DisplayName("同値分割: X-Request-Id ヘッダが無ければ UUID を採番し、MDC とレスポンスヘッダに載せる")
    void generatesRequestIdWhenHeaderMissing() throws Exception {
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request("GET", "/api/posts/1"), response, chain);

        String requestId = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(requestId).hasSize(36); // UUID
        assertThat(chain.mdcAtChain)
            .containsEntry(RequestLoggingFilter.MDC_REQUEST_ID, requestId)
            .containsEntry(RequestLoggingFilter.MDC_HTTP_METHOD, "GET")
            .containsEntry(RequestLoggingFilter.MDC_HTTP_PATH, "/api/posts/1")
            .containsEntry(RequestLoggingFilter.MDC_CLIENT_IP, "10.0.0.1");
    }

    @Test
    @DisplayName("同値分割: 正しい形式の X-Request-Id ヘッダはそのまま引き継ぐ")
    void propagatesValidIncomingRequestId() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/timeline/all");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "1-abc_DEF-123");
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("1-abc_DEF-123");
        assertThat(chain.mdcAtChain).containsEntry(RequestLoggingFilter.MDC_REQUEST_ID, "1-abc_DEF-123");
    }

    @ParameterizedTest(name = "同値分割: 不正な X-Request-Id [{0}] は引き継がず採番し直す")
    @ValueSource(strings = {"", "   ", "abc def", "line\nbreak", "日本語", "a=b", "<script>"})
    void regeneratesWhenIncomingRequestIdIsInvalid(String incoming) throws Exception {
        MockHttpServletRequest request = request("GET", "/api/timeline/all");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, incoming);

        filter.doFilter(request, response, new CapturingChain());

        String requestId = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(requestId).isNotEqualTo(incoming).hasSize(36);
    }

    @ParameterizedTest(name = "境界値分析: X-Request-Id の長さ {0} → 引き継ぐ={1}")
    @CsvSource({"1, true", "64, true", "65, false"})
    void requestIdLengthBoundary(int length, boolean propagated) throws Exception {
        String incoming = "x".repeat(length);
        MockHttpServletRequest request = request("GET", "/api/timeline/all");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, incoming);

        filter.doFilter(request, response, new CapturingChain());

        String requestId = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        if (propagated) {
            assertThat(requestId).isEqualTo(incoming);
        } else {
            assertThat(requestId).isNotEqualTo(incoming).hasSize(36);
        }
    }

    @Test
    @DisplayName("X-Forwarded-For があれば先頭の IP をクライアント IP として記録する")
    void prefersForwardedForHeader() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/timeline/all");
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.2");
        CapturingChain chain = new CapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.mdcAtChain).containsEntry(RequestLoggingFilter.MDC_CLIENT_IP, "203.0.113.5");
    }

    @ParameterizedTest(name = "分岐網羅: ステータス {0} のアクセスログは {1} レベル")
    @CsvSource({"200, INFO", "201, INFO", "404, INFO", "401, INFO", "500, ERROR", "503, ERROR"})
    void accessLogLevelFollowsStatus(int status, String expectedLevel) throws Exception {
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                ((MockHttpServletResponse) res).setStatus(status);
            }
        };

        filter.doFilter(request("DELETE", "/api/posts/9"), response, chain);

        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(1);
        ILoggingEvent event = events.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.valueOf(expectedLevel));
        assertThat(event.getFormattedMessage()).startsWith("DELETE /api/posts/9 -> " + status);
        // ログ出力時点の MDC にステータスと所要時間が入っている
        assertThat(event.getMDCPropertyMap())
            .containsEntry(RequestLoggingFilter.MDC_HTTP_STATUS, String.valueOf(status))
            .containsKey(RequestLoggingFilter.MDC_DURATION);
        assertThat(Long.parseLong(event.getMDCPropertyMap().get(RequestLoggingFilter.MDC_DURATION))).isNotNegative();
    }

    @Test
    @DisplayName("分岐網羅: /actuator 配下はアクセスログを出さず、リクエスト ID も付けない")
    void skipsActuatorEndpoints() throws Exception {
        filter.doFilter(request("GET", "/actuator/health/liveness"), response, new CapturingChain());

        assertThat(appender.list).isEmpty();
        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNull();
    }

    @Test
    @DisplayName("状態遷移: /actuator でもチェーン内で書かれた MDC（usr.id など）は終了時に clear される（他リクエストへの漏れ防止）")
    void clearsMdcWrittenInsideChainEvenForActuator() throws Exception {
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                // セキュリティチェーン内の JwtAuthenticationFilter が認証成功時に書く値を模す
                MDC.put("usr.id", "1");
            }
        };

        filter.doFilter(request("GET", "/actuator/prometheus"), response, chain);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("状態遷移: チェーンが例外で抜けても 500 として ERROR を記録し、MDC を clear して例外を再送出する")
    void logsErrorAndClearsMdcWhenChainThrows() {
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) throws ServletException {
                throw new ServletException("boom");
            }
        };

        assertThatThrownBy(() -> filter.doFilter(request("POST", "/api/posts"), response, chain))
            .isInstanceOf(ServletException.class);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getMDCPropertyMap()).containsEntry(RequestLoggingFilter.MDC_HTTP_STATUS, "500");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("状態遷移: 正常終了後も MDC は clear されている（スレッドプール再利用時の漏れ防止）")
    void clearsMdcAfterNormalCompletion() throws Exception {
        filter.doFilter(request("GET", "/api/timeline/all"), response, new CapturingChain());

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
