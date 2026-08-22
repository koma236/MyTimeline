package com.example.mytimeline.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mytimeline.model.RefreshToken;
import com.example.mytimeline.model.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * refresh_tokens テーブルの SQL を H2 で検証する。
 *
 * <p>設計技法: 状態遷移（有効 → 失効。失効済みの再失効は最初の時刻を保持）。
 * revokeAllByUserId は「対象ユーザーの有効なトークンだけ」を失効させることを、
 * 他ユーザーのトークンと失効済みトークンを混ぜた状態で確認する。</p>
 */
class RefreshTokenMapperTest extends MapperTestBase {

    private static final LocalDateTime REVOKED_FIRST = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime REVOKED_LATER = LocalDateTime.of(2026, 1, 2, 0, 0);

    @Autowired
    private RefreshTokenMapper refreshTokenMapper;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = insertUser("alice");
        bob = insertUser("bob");
    }

    /** token_hash は CHAR(64)。SHA-256 の 16 進表現と同じ長さの文字列を作る。 */
    private static String hash(char c) {
        return String.valueOf(c).repeat(64);
    }

    private RefreshToken insertToken(Long userId, char hashChar) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(hash(hashChar));
        token.setExpiresAt(LocalDateTime.of(2030, 1, 1, 0, 0));
        refreshTokenMapper.insert(token);
        return token;
    }

    @Test
    @DisplayName("insert で id が採番され、findByTokenHash で取り出せる（revoked_at は null）")
    void insertAndFindByTokenHash() {
        RefreshToken token = insertToken(alice.getId(), 'a');

        assertThat(token.getId()).isNotNull();
        RefreshToken found = refreshTokenMapper.findByTokenHash(hash('a')).orElseThrow();
        assertThat(found.getId()).isEqualTo(token.getId());
        assertThat(found.getUserId()).isEqualTo(alice.getId());
        assertThat(found.getExpiresAt()).isEqualTo(LocalDateTime.of(2030, 1, 1, 0, 0));
        assertThat(found.getRevokedAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("findByTokenHash は存在しないハッシュなら empty")
    void findByUnknownHashReturnsEmpty() {
        assertThat(refreshTokenMapper.findByTokenHash(hash('z'))).isEmpty();
    }

    @Test
    @DisplayName("エラー推測: 同じ token_hash の二重登録は UNIQUE 制約で拒否される")
    void duplicateHashIsRejected() {
        insertToken(alice.getId(), 'a');

        assertThatThrownBy(() -> insertToken(bob.getId(), 'a')).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("状態遷移: 有効 → revokeById で失効し、revoked_at に渡した時刻が入る")
    void revokeByIdSetsRevokedAt() {
        RefreshToken token = insertToken(alice.getId(), 'a');

        assertThat(refreshTokenMapper.revokeById(token.getId(), REVOKED_FIRST)).isEqualTo(1);

        RefreshToken found = refreshTokenMapper.findByTokenHash(hash('a')).orElseThrow();
        assertThat(found.getRevokedAt()).isEqualTo(REVOKED_FIRST);
        assertThat(found.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("状態遷移: 失効済 → revokeById は 0 行で、最初の失効時刻を上書きしない")
    void revokeByIdDoesNotOverwriteEarlierRevocation() {
        RefreshToken token = insertToken(alice.getId(), 'a');
        refreshTokenMapper.revokeById(token.getId(), REVOKED_FIRST);

        assertThat(refreshTokenMapper.revokeById(token.getId(), REVOKED_LATER)).isZero();

        assertThat(refreshTokenMapper.findByTokenHash(hash('a')).orElseThrow().getRevokedAt()).isEqualTo(REVOKED_FIRST);
    }

    @Test
    @DisplayName("revokeAllByUserId は対象ユーザーの有効なトークンだけを失効させる")
    void revokeAllByUserIdAffectsOnlyActiveTokensOfUser() {
        insertToken(alice.getId(), 'a');
        insertToken(alice.getId(), 'b');
        RefreshToken alreadyRevoked = insertToken(alice.getId(), 'c');
        refreshTokenMapper.revokeById(alreadyRevoked.getId(), REVOKED_FIRST);
        insertToken(bob.getId(), 'd');

        int revoked = refreshTokenMapper.revokeAllByUserId(alice.getId(), REVOKED_LATER);

        assertThat(revoked).isEqualTo(2);
        assertThat(refreshTokenMapper.findByTokenHash(hash('a')).orElseThrow().getRevokedAt()).isEqualTo(REVOKED_LATER);
        assertThat(refreshTokenMapper.findByTokenHash(hash('b')).orElseThrow().getRevokedAt()).isEqualTo(REVOKED_LATER);
        // 失効済みは最初の時刻のまま
        assertThat(refreshTokenMapper.findByTokenHash(hash('c')).orElseThrow().getRevokedAt()).isEqualTo(REVOKED_FIRST);
        // 他ユーザーは触らない
        assertThat(refreshTokenMapper.findByTokenHash(hash('d')).orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("revokeAllByUserId は有効なトークンが無ければ 0 行")
    void revokeAllByUserIdWithNoActiveTokens() {
        assertThat(refreshTokenMapper.revokeAllByUserId(alice.getId(), REVOKED_LATER)).isZero();
    }

    @Test
    @DisplayName("状態: ユーザー削除でトークンも CASCADE で消える")
    void deletingUserCascadesTokens() {
        insertToken(alice.getId(), 'a');

        jdbc.update("DELETE FROM users WHERE id = ?", alice.getId());

        assertThat(refreshTokenMapper.findByTokenHash(hash('a'))).isEmpty();
    }
}
