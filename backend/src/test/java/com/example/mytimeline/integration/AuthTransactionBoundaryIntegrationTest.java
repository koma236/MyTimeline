package com.example.mytimeline.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mytimeline.service.RefreshTokenService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 認証のトランザクション境界を検証する（#67）。
 *
 * <p>BCrypt は約 80 ミリ秒の CPU 処理で、トランザクションの中で実行すると、その間ずっと DB 接続を
 * 握り続ける。ログインが集中すると接続プールが占有され、無関係な API まで遅くなる。
 * そのため「BCrypt の実行中はトランザクションが開いていない」ことを、実際の Bean の配線で確かめる。
 * プロキシ越しの {@code @Transactional} の効き方はモックでは再現できないので結合テストにしている。</p>
 *
 * <p>設計技法: デシジョンテーブル。BCrypt を呼ぶ経路（signup の encode / login の matches で
 * 成功・失敗）ごとに、呼び出し時点でトランザクションが無いことを見る。
 * あわせてエラー推測として、トランザクションを狭めたことで signup の原子性
 * （ユーザーの INSERT とリフレッシュトークン発行）が壊れていないことを確かめる。</p>
 */
class AuthTransactionBoundaryIntegrationTest extends IntegrationTestBase {

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private RefreshTokenService refreshTokenService;

    /** BCrypt が呼ばれた時点でトランザクションが開いていたかを、呼ばれた順に記録する。 */
    private final List<Boolean> transactionActiveDuringBcrypt = new ArrayList<>();

    private void recordTransactionStateOnEncode() {
        doAnswer(invocation -> {
            transactionActiveDuringBcrypt.add(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(passwordEncoder).encode(any());
    }

    private void recordTransactionStateOnMatches() {
        doAnswer(invocation -> {
            transactionActiveDuringBcrypt.add(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(passwordEncoder).matches(any(), anyString());
    }

    private long countUsers(String username) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Long.class, username);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("デシジョンテーブル（signup）: パスワードのハッシュ化はトランザクションの外で行う")
    void signupHashesPasswordOutsideTransaction() throws Exception {
        recordTransactionStateOnEncode();

        signup("alice");

        assertThat(transactionActiveDuringBcrypt).containsExactly(false);
    }

    @Test
    @DisplayName("デシジョンテーブル（login 成功）: パスワードの照合はトランザクションの外で行う")
    void loginVerifiesPasswordOutsideTransaction() throws Exception {
        signup("alice");
        recordTransactionStateOnMatches();

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("alice", PASSWORD)))
            .andExpect(status().isOk());

        assertThat(transactionActiveDuringBcrypt).containsExactly(false);
    }

    @Test
    @DisplayName("デシジョンテーブル（login 失敗）: パスワード誤りの照合もトランザクションの外で行う")
    void loginRejectsWrongPasswordOutsideTransaction() throws Exception {
        signup("alice");
        recordTransactionStateOnMatches();

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("alice", "wrong-password")))
            .andExpect(status().isUnauthorized());

        assertThat(transactionActiveDuringBcrypt).containsExactly(false);
    }

    @Test
    @DisplayName("エラー推測: リフレッシュトークンの発行に失敗したら、ユーザーの INSERT もロールバックされる")
    void signupRollsBackUserWhenRefreshTokenIssueFails() throws Exception {
        doThrow(new IllegalStateException("発行失敗")).when(refreshTokenService).issue(any());

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupJson("alice", "alice@example.com")))
            .andExpect(status().isInternalServerError());

        // ユーザーだけが残ると、登録に失敗したように見えるのに同じユーザー名で登録し直せなくなる
        assertThat(countUsers("alice")).isZero();
    }
}
