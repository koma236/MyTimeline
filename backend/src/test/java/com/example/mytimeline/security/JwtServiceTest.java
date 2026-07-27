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

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60, 14, false));

    private static User user(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    @Test
    @DisplayName("発行したトークンから userId と username を復元できる")
    void generateAndParse() {
        String token = jwtService.generateAccessToken(user(42L, "taro"));

        Optional<CurrentUser> parsed = jwtService.parseToken(token);

        assertThat(parsed).contains(new CurrentUser(42L, "taro"));
    }

    @Test
    @DisplayName("署名を書き換えたトークンは拒否される")
    void rejectsTamperedSignature() {
        String token = jwtService.generateAccessToken(user(42L, "taro"));
        String[] parts = token.split("\\.");

        // 文字列の末尾をいじる方法は使えない。HS256 の署名 32 バイトを Base64URL にすると
        // 43 文字 = 258 ビットになり、最後の 1 文字の下位 2 ビットはデコード時に捨てられる。
        // そのため 'A'〜'D' はいずれも同じバイト列にデコードされ、署名が変わらないことがある。
        // バイト列に戻して 1 ビット反転させれば、確実に別の署名になる。
        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        signature[0] ^= 0x01;
        String tampered = parts[0] + "." + parts[1] + "."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

        assertThat(tampered).isNotEqualTo(token);
        assertThat(jwtService.parseToken(tampered)).isEmpty();
    }

    @Test
    @DisplayName("ペイロードを書き換えたトークンは拒否される（別ユーザーへのなりすまし防止）")
    void rejectsTamperedPayload() {
        String token = jwtService.generateAccessToken(user(42L, "taro"));
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
        String token = jwtService.generateAccessToken(user(42L, "taro"));
        String[] parts = token.split("\\.");

        String header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(jwtService.parseToken(header + "." + parts[1] + ".")).isEmpty();
    }

    @Test
    @DisplayName("別の鍵で署名されたトークンは拒否される")
    void rejectsTokenSignedWithAnotherKey() {
        JwtService other = new JwtService(
            new JwtProperties("another-secret-key-for-unit-test-at-least-32-bytes", 60, 14, false)
        );
        String token = other.generateAccessToken(user(42L, "taro"));

        assertThat(jwtService.parseToken(token)).isEmpty();
    }

    @Test
    @DisplayName("有効期限切れのトークンは拒否される")
    void rejectsExpiredToken() {
        // 有効期間 -1 分 = 発行時点ですでに期限切れ
        JwtService expiring = new JwtService(new JwtProperties(SECRET, -1, 14, false));
        String token = expiring.generateAccessToken(user(42L, "taro"));

        assertThat(jwtService.parseToken(token)).isEmpty();
    }

    @Test
    @DisplayName("トークン形式でない文字列は拒否される")
    void rejectsGarbage() {
        assertThat(jwtService.parseToken("not-a-jwt")).isEmpty();
    }
}
