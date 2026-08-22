package com.example.mytimeline.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseCookie;

/**
 * リフレッシュトークン Cookie の属性を検証する。
 *
 * <p>Cookie の属性は 1 つ欠けるだけでセキュリティ上の意味が変わる
 * （httpOnly が無ければ XSS で持ち出せる、Path が広ければ全 API に付いて回る）ため、
 * 値の組み立てを 1 箇所に集約したこのクラスで属性を全部見る。</p>
 */
class RefreshCookieFactoryTest {

    private static final long REFRESH_DAYS = 14;

    private static RefreshCookieFactory factory(boolean secure) {
        return new RefreshCookieFactory(new JwtProperties("secret", 15, REFRESH_DAYS, secure));
    }

    @Test
    @DisplayName("create は httpOnly / SameSite=Lax / Path=/api/auth で、有効期限はリフレッシュトークンの日数")
    void createSetsProtectiveAttributes() {
        ResponseCookie cookie = factory(false).create("raw-token");

        assertThat(cookie.getName()).isEqualTo(RefreshCookieFactory.COOKIE_NAME);
        assertThat(cookie.getValue()).isEqualTo("raw-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(REFRESH_DAYS));
    }

    @ParameterizedTest(name = "Secure 属性は設定値に従う: refreshCookieSecure={0}")
    @ValueSource(booleans = {true, false})
    void secureFollowsConfiguration(boolean secure) {
        assertThat(factory(secure).create("raw-token").isSecure()).isEqualTo(secure);
        assertThat(factory(secure).expired().isSecure()).isEqualTo(secure);
    }

    @Test
    @DisplayName("expired は値が空で maxAge=0（ブラウザに即時削除させる）")
    void expiredIsEmptyAndImmediatelyExpiring() {
        ResponseCookie cookie = factory(false).expired();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("エラー推測: expired の名前・Path・httpOnly・SameSite は create と一致する（一致しないとブラウザが削除しない）")
    void expiredMatchesCreateAttributes() {
        ResponseCookie created = factory(true).create("raw-token");
        ResponseCookie expired = factory(true).expired();

        assertThat(expired.getName()).isEqualTo(created.getName());
        assertThat(expired.getPath()).isEqualTo(created.getPath());
        assertThat(expired.isHttpOnly()).isEqualTo(created.isHttpOnly());
        assertThat(expired.getSameSite()).isEqualTo(created.getSameSite());
        assertThat(expired.isSecure()).isEqualTo(created.isSecure());
    }

    @Test
    @DisplayName("Set-Cookie ヘッダとして直列化しても属性が落ちない")
    void serializesAllAttributes() {
        String header = factory(true).create("raw-token").toString();

        assertThat(header)
            .startsWith("refreshToken=raw-token;")
            .contains("Path=/api/auth")
            .contains("HttpOnly")
            .contains("Secure")
            .contains("SameSite=Lax")
            .contains("Max-Age=" + Duration.ofDays(REFRESH_DAYS).toSeconds());
    }
}
