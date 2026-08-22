package com.example.mytimeline.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link JwtAuthenticationFilter} の判定を検証する。
 *
 * <p>設計技法: デシジョンテーブル。
 * <pre>
 *   Authorization ヘッダ        | トークン検証 | 認証セット | チェーン続行
 *   なし                         | 行わない     | しない     | する
 *   "Bearer " のみ / 空白のみ     | 行わない     | しない     | する
 *   "Basic xxx"（prefix 違い）    | 行わない     | しない     | する
 *   "Bearer &lt;invalid&gt;"     | 行う → 失敗  | しない     | する
 *   "Bearer &lt;valid&gt;"       | 行う → 成功  | する       | する
 * </pre>
 * 「拒否はしない（401 は SecurityConfig の役割）」ため、どの行でもチェーンは続行する。</p>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService);
        request = new MockHttpServletRequest("GET", "/api/posts/1");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("有効な Bearer トークンなら CurrentUser を認証情報としてセットし、チェーンを続行する")
    void validTokenAuthenticates() throws Exception {
        request.addHeader("Authorization", "Bearer valid.jwt.token");
        when(jwtService.parseToken("valid.jwt.token")).thenReturn(Optional.of(new CurrentUser(7L, "taro")));

        filter.doFilter(request, response, chain);

        Authentication authentication = currentAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new CurrentUser(7L, "taro"));
        assertThat(authentication.isAuthenticated()).isTrue();
        // ロールは扱わない
        assertThat(authentication.getAuthorities()).isEmpty();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("認証に成功したら MDC の usr.id にユーザー ID（ID のみ・username は載せない）を入れる")
    void validTokenPutsUserIdIntoMdc() throws Exception {
        request.addHeader("Authorization", "Bearer valid.jwt.token");
        when(jwtService.parseToken("valid.jwt.token")).thenReturn(Optional.of(new CurrentUser(7L, "taro")));

        filter.doFilter(request, response, chain);

        assertThat(MDC.get(JwtAuthenticationFilter.MDC_USER_ID)).isEqualTo("7");
        assertThat(MDC.getCopyOfContextMap()).doesNotContainValue("taro");
    }

    @Test
    @DisplayName("認証しなかった場合は MDC に usr.id を入れない")
    void invalidTokenLeavesMdcEmpty() throws Exception {
        request.addHeader("Authorization", "Bearer broken");
        when(jwtService.parseToken("broken")).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(MDC.get(JwtAuthenticationFilter.MDC_USER_ID)).isNull();
    }

    @Test
    @DisplayName("トークンの前後の空白は取り除いてから検証する")
    void tokenIsTrimmed() throws Exception {
        request.addHeader("Authorization", "Bearer   valid.jwt.token  ");
        when(jwtService.parseToken("valid.jwt.token")).thenReturn(Optional.of(new CurrentUser(7L, "taro")));

        filter.doFilter(request, response, chain);

        assertThat(currentAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("検証に失敗するトークンは認証情報をセットせず、それでもチェーンは続行する（拒否は SecurityConfig の役割）")
    void invalidTokenDoesNotAuthenticateButContinues() throws Exception {
        request.addHeader("Authorization", "Bearer broken");
        when(jwtService.parseToken("broken")).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(currentAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest(name = "デシジョンテーブル: Authorization=[{0}] はトークン検証を行わずチェーンを続行する")
    @NullSource
    @ValueSource(strings = {"", "Bearer", "Bearer ", "Bearer    ", "Basic dXNlcjpwYXNz", "bearer lower.case.prefix", "Token abc"})
    void headerWithoutBearerTokenSkipsVerification(String header) throws Exception {
        if (header != null) {
            request.addHeader("Authorization", header);
        }

        filter.doFilter(request, response, chain);

        verify(jwtService, never()).parseToken(anyString());
        assertThat(currentAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
