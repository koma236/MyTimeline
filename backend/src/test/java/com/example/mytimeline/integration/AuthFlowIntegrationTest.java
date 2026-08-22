package com.example.mytimeline.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 認証フロー（F01）を HTTP から DB まで通して検証する。
 *
 * <p>設計技法: 状態遷移。リフレッシュトークンは
 * 「発行 → 使用（ローテーションで失効 + 新規発行）→ 失効済みの再提示（盗用検知で全失効）」
 * と遷移する。各遷移の後に DB の refresh_tokens を直接見て、状態が実際に変わったことを確かめる。</p>
 */
class AuthFlowIntegrationTest extends IntegrationTestBase {

    private long countActiveTokens(long userId) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Long.class, userId);
        return count == null ? 0 : count;
    }

    private MvcResult refresh(Cookie refreshCookie) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie)).andReturn();
    }

    @Test
    @DisplayName("signup で users に行が入り、返ったアクセストークンで /me が同じユーザーを返す")
    void signupPersistsUserAndIssuesUsableToken() throws Exception {
        Session session = signup("alice");

        assertThat(session.refreshCookie()).isNotNull();
        assertThat(session.refreshCookie().isHttpOnly()).isTrue();
        assertThat(session.refreshCookie().getPath()).isEqualTo("/api/auth");
        assertThat(countActiveTokens(session.userId())).isEqualTo(1);

        mockMvc.perform(get("/api/auth/me").header("Authorization", session.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(session.userId()))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("同値分割: username でも email でもログインできる")
    void loginByUsernameOrEmail() throws Exception {
        signup("alice");

        for (String identifier : new String[] {"alice", "alice@example.com"}) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(identifier, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(cookie().exists("refreshToken"));
        }
    }

    @Test
    @DisplayName("同値分割: パスワード誤り・存在しないユーザーはどちらも 401（どちらが誤りかは明かさない）")
    void loginFailsWithSameStatusForWrongPasswordAndUnknownUser() throws Exception {
        signup("alice");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("alice", "wrong-password")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").isString());
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("nobody", PASSWORD)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("エラー推測: username / email の重複は 409 で、どの項目かを fieldErrors で返す")
    void duplicateSignupReturnsConflict() throws Exception {
        signup("alice");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupJson("alice", "other@example.com")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.fieldErrors.username").isString());
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupJson("alice2", "alice@example.com")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.fieldErrors.email").isString());
    }

    @Test
    @DisplayName("状態遷移: refresh は旧トークンを失効させ新トークンを発行する（ローテーション）")
    void refreshRotatesToken() throws Exception {
        Session session = signup("alice");

        MvcResult result = refresh(session.refreshCookie());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Cookie rotated = result.getResponse().getCookie("refreshToken");
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(session.refreshCookie().getValue());
        // 発行 2 本のうち有効なのは新しい 1 本だけ
        assertThat(countActiveTokens(session.userId())).isEqualTo(1);

        // 新しいトークンで続けて refresh できる
        assertThat(refresh(rotated).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("状態遷移: 失効済みトークンの再提示は 401 になり、そのユーザーの全セッションが失効する（盗用検知）")
    void reusingRevokedTokenRevokesAllSessions() throws Exception {
        Session session = signup("alice");
        Cookie rotated = refresh(session.refreshCookie()).getResponse().getCookie("refreshToken");
        assertThat(rotated).isNotNull();

        // 盗まれた（＝ローテーション前の）トークンが使われた
        MvcResult reuse = refresh(session.refreshCookie());

        assertThat(reuse.getResponse().getStatus()).isEqualTo(401);
        // 全失効は REQUIRES_NEW の別トランザクションでコミットされるため、例外で元の処理が
        // ロールバックされても DB に残る。正規の新トークンも使えなくなっている
        assertThat(countActiveTokens(session.userId())).isZero();
        assertThat(refresh(rotated).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("状態遷移: 期限切れトークンは 401（失効にはならず、他のセッションは影響を受けない）")
    void expiredTokenIsRejectedWithoutRevokingOthers() throws Exception {
        Session session = signup("alice");
        jdbc.update("UPDATE refresh_tokens SET expires_at = ? WHERE user_id = ?",
            LocalDateTime.now().minusMinutes(1), session.userId());

        assertThat(refresh(session.refreshCookie()).getResponse().getStatus()).isEqualTo(401);

        // 期限切れは「盗用」ではないので全失効はしない
        assertThat(countActiveTokens(session.userId())).isEqualTo(1);
    }

    @Test
    @DisplayName("デシジョンテーブル: Cookie なし / 存在しないトークン はどちらも 401")
    void refreshWithoutValidCookieIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", "unknown-token")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("状態遷移: logout で全セッションが失効し Cookie が削除され、以後 refresh できない")
    void logoutRevokesAllSessionsAndClearsCookie() throws Exception {
        Session session = signup("alice");
        // 別端末のセッションも用意しておく
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("alice", PASSWORD)))
            .andExpect(status().isOk());
        assertThat(countActiveTokens(session.userId())).isEqualTo(2);

        mockMvc.perform(post("/api/auth/logout").cookie(session.refreshCookie()))
            .andExpect(status().isNoContent())
            .andExpect(cookie().maxAge("refreshToken", 0));

        assertThat(countActiveTokens(session.userId())).isZero();
        assertThat(refresh(session.refreshCookie()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("logout は Cookie が無くても 204（ログアウト済みという結果は同じ）")
    void logoutWithoutCookieSucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("デシジョンテーブル: 認証ヘッダなし / 壊れたトークン は保護 API で 401")
    void protectedEndpointRejectsMissingOrInvalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt"))
            .andExpect(status().isUnauthorized());
    }
}
