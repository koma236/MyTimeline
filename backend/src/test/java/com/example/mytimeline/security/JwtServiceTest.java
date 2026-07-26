package com.example.mytimeline.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mytimeline.model.User;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-for-unit-test-at-least-32-bytes";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60));

    private static User user(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    @Test
    @DisplayName("発行したトークンから userId と username を復元できる")
    void generateAndParse() {
        String token = jwtService.generateToken(user(42L, "taro"));

        Optional<CurrentUser> parsed = jwtService.parseToken(token);

        assertThat(parsed).contains(new CurrentUser(42L, "taro"));
    }

    @Test
    @DisplayName("署名を書き換えたトークンは拒否される")
    void rejectsTamperedSignature() {
        String token = jwtService.generateToken(user(42L, "taro"));
        // 末尾に文字を足すだけでは Base64URL デコード時に捨てられて同じ署名になり得るため、
        // 最後の 1 文字を別の文字に「置き換える」ことで確実に署名を変える
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThat(jwtService.parseToken(tampered)).isEmpty();
    }

    @Test
    @DisplayName("ペイロードを書き換えたトークンは拒否される（別ユーザーへのなりすまし防止）")
    void rejectsTamperedPayload() {
        String token = jwtService.generateToken(user(42L, "taro"));
        String[] parts = token.split("\\.");

        Base64.Decoder decoder = Base64.getUrlDecoder();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String payload = new String(decoder.decode(parts[1]), StandardCharsets.UTF_8);
        String tamperedPayload = payload.replace("\"sub\":\"42\"", "\"sub\":\"999\"");
        assertThat(tamperedPayload).isNotEqualTo(payload);

        String tampered = parts[0]
            + "." + encoder.encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8))
            + "." + parts[2];

        assertThat(jwtService.parseToken(tampered)).isEmpty();
    }

    @Test
    @DisplayName("alg を none にした署名なしトークンは拒否される")
    void rejectsUnsignedToken() {
        String token = jwtService.generateToken(user(42L, "taro"));
        String[] parts = token.split("\\.");

        String header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(jwtService.parseToken(header + "." + parts[1] + ".")).isEmpty();
    }

    @Test
    @DisplayName("別の鍵で署名されたトークンは拒否される")
    void rejectsTokenSignedWithAnotherKey() {
        JwtService other = new JwtService(
            new JwtProperties("another-secret-key-for-unit-test-at-least-32-bytes", 60)
        );
        String token = other.generateToken(user(42L, "taro"));

        assertThat(jwtService.parseToken(token)).isEmpty();
    }

    @Test
    @DisplayName("有効期限切れのトークンは拒否される")
    void rejectsExpiredToken() {
        // 有効期間 -1 分 = 発行時点ですでに期限切れ
        JwtService expiring = new JwtService(new JwtProperties(SECRET, -1));
        String token = expiring.generateToken(user(42L, "taro"));

        assertThat(jwtService.parseToken(token)).isEmpty();
    }

    @Test
    @DisplayName("トークン形式でない文字列は拒否される")
    void rejectsGarbage() {
        assertThat(jwtService.parseToken("not-a-jwt")).isEmpty();
    }
}
