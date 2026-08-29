package com.example.mytimeline.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mytimeline.logging.RequestLoggingFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * ログ・監視まわりがアプリ全体として配線されていることを確認する結合テスト。
 *
 * <p>設計技法: デシジョンテーブル（エンドポイント × 認証の有無 → 期待ステータス）。
 * <pre>
 *   エンドポイント                  | 未認証 | 認証済み
 *   /actuator/health               | 200    | 200
 *   /actuator/health/liveness      | 200    | 200
 *   /actuator/health/readiness     | 200    | 200
 *   /actuator/prometheus           | 401    | 200
 *   /actuator/metrics              | 401    | 200
 *   /actuator/info                 | 401    | 200
 * </pre>
 * JSON 形式のログ出力そのものはここでは検証しない（ログシステムの初期化は Spring コンテキストより
 * 前に行われ、テスト JVM 全体に影響するため）。手順は docs/10_logging_design.md の動作確認を参照。</p>
 */
class ObservabilityIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("API のレスポンスには X-Request-Id が付く（未認証の 401 でも付く）")
    void apiResponsesCarryRequestId() throws Exception {
        MvcResult unauthenticated = mockMvc.perform(get("/api/timeline/all"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().exists(RequestLoggingFilter.REQUEST_ID_HEADER))
            .andReturn();
        assertThat(unauthenticated.getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).hasSize(36);

        Session session = signup("observer");
        mockMvc.perform(get("/api/timeline/all").header("Authorization", session.bearer()))
            .andExpect(status().isOk())
            .andExpect(header().exists(RequestLoggingFilter.REQUEST_ID_HEADER));
    }

    @Test
    @DisplayName("クライアントが送った X-Request-Id はそのままレスポンスに返る（経路全体で同じ ID を追える）")
    void incomingRequestIdIsEchoed() throws Exception {
        mockMvc.perform(get("/api/timeline/all").header(RequestLoggingFilter.REQUEST_ID_HEADER, "alb-trace-0001"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(RequestLoggingFilter.REQUEST_ID_HEADER, "alb-trace-0001"));
    }

    @Test
    @DisplayName("デシジョンテーブル: health / liveness / readiness は未認証で 200、詳細（components）は出さない")
    void healthProbesArePublicWithoutDetails() throws Exception {
        for (String path : new String[] {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                // ヘルスチェックはアクセスログ対象外なのでリクエスト ID も付かない
                .andExpect(header().doesNotExist(RequestLoggingFilter.REQUEST_ID_HEADER));
        }
    }

    @Test
    @DisplayName("デシジョンテーブル: prometheus / metrics / info は未認証だと 401、認証済みなら 200")
    void metricsEndpointsRequireAuthentication() throws Exception {
        for (String path : new String[] {"/actuator/prometheus", "/actuator/metrics", "/actuator/info"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }

        Session session = signup("observer");
        MvcResult prometheus = mockMvc.perform(get("/actuator/prometheus").header("Authorization", session.bearer()))
            .andExpect(status().isOk())
            .andReturn();
        String body = prometheus.getResponse().getContentAsString();
        // 監視設計（docs/11_monitoring_design.md）が前提にしているメトリクスが出ている
        assertThat(body).contains("jvm_memory_used_bytes");
        assertThat(body).contains("application=\"mytimeline\"");

        mockMvc.perform(get("/actuator/metrics").header("Authorization", session.bearer())).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/info").header("Authorization", session.bearer())).andExpect(status().isOk());
    }
}
