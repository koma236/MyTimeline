package com.example.mytimeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mytimeline.exception.InvalidRefreshTokenException;
import com.example.mytimeline.mapper.RefreshTokenMapper;
import com.example.mytimeline.model.RefreshToken;
import com.example.mytimeline.security.JwtProperties;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private RefreshTokenMapper refreshTokenMapper;

    @Mock
    private RefreshTokenRevoker refreshTokenRevoker;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
            refreshTokenMapper,
            refreshTokenRevoker,
            new JwtProperties("test-secret-key-for-unit-test-at-least-32-bytes", 15, 14, false)
        );
    }

    /**
     * 有効なトークンが 1 件保存されている状態にする。
     *
     * <p>ハッシュ化はサービス内部で行われテストからは再現できないため、
     * 問い合わせられたハッシュをそのまま持つ行を返す。</p>
     */
    private void givenValidStoredToken() {
        when(refreshTokenMapper.findByTokenHash(any())).thenAnswer(invocation -> {
            RefreshToken row = new RefreshToken();
            row.setId(100L);
            row.setUserId(USER_ID);
            row.setTokenHash(invocation.getArgument(0));
            row.setExpiresAt(LocalDateTime.now().plusDays(14));
            return Optional.of(row);
        });
    }

    @Test
    @DisplayName("発行したトークンは十分な長さのランダム値で、毎回異なる")
    void issueGeneratesUniqueOpaqueTokens() {
        String first = refreshTokenService.issue(USER_ID);
        String second = refreshTokenService.issue(USER_ID);

        assertThat(first).isNotEqualTo(second);
        // 32 バイトを Base64URL（パディングなし）にすると 43 文字
        assertThat(first).hasSize(43);
        // JWT ではないのでドット区切りにならない
        assertThat(first).doesNotContain(".");
    }

    @Test
    @DisplayName("DB には生値ではなく SHA-256 ハッシュを保存する")
    void issueStoresHashNotRawToken() {
        String rawToken = refreshTokenService.issue(USER_ID);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenMapper).insert(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getTokenHash()).hasSize(64).matches("[0-9a-f]+");
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(13));
    }

    @Test
    @DisplayName("リフレッシュすると旧トークンを失効させ、新しいトークンを発行する")
    void rotateRevokesOldAndIssuesNew() {
        givenValidStoredToken();

        RefreshTokenService.RotationResult result = refreshTokenService.rotate("old-token");

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.rawToken()).isNotEqualTo("old-token").hasSize(43);
        verify(refreshTokenMapper).revokeById(eq(100L), any());
        verify(refreshTokenMapper).insert(any());
    }

    @Test
    @DisplayName("存在しないトークンでのリフレッシュは拒否される")
    void rotateRejectsUnknownToken() {
        when(refreshTokenMapper.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("unknown"))
            .isInstanceOf(InvalidRefreshTokenException.class)
            .hasMessage(InvalidRefreshTokenException.MESSAGE);

        verify(refreshTokenMapper, never()).insert(any());
    }

    @Test
    @DisplayName("期限切れトークンでのリフレッシュは拒否される")
    void rotateRejectsExpiredToken() {
        RefreshToken expired = new RefreshToken();
        expired.setId(100L);
        expired.setUserId(USER_ID);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(refreshTokenMapper.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate("expired"))
            .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenMapper, never()).insert(any());
    }

    @Test
    @DisplayName("失効済みトークンの再提示は盗用とみなし、そのユーザーの全セッションを失効させる")
    void rotateDetectsTokenReuse() {
        RefreshToken revoked = new RefreshToken();
        revoked.setId(100L);
        revoked.setUserId(USER_ID);
        revoked.setExpiresAt(LocalDateTime.now().plusDays(14));
        revoked.setRevokedAt(LocalDateTime.now().minusMinutes(5));
        when(refreshTokenMapper.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.rotate("stolen"))
            .isInstanceOf(InvalidRefreshTokenException.class);

        // 例外でロールバックされないよう、失効は別トランザクション側へ委譲する
        verify(refreshTokenRevoker).revokeAllByUserId(eq(USER_ID), any());
        verify(refreshTokenMapper, never()).revokeAllByUserId(any(), any());
        verify(refreshTokenMapper, never()).insert(any());
    }

    @Test
    @DisplayName("ログアウトはそのユーザーの全セッションを失効させる")
    void revokeInvalidatesAllSessions() {
        givenValidStoredToken();

        refreshTokenService.revoke("current-token");

        verify(refreshTokenMapper).revokeAllByUserId(eq(USER_ID), any());
    }

    @Test
    @DisplayName("トークンが無い・空のログアウトは何もせず、例外にもしない")
    void revokeIgnoresMissingToken() {
        refreshTokenService.revoke(null);
        refreshTokenService.revoke("  ");

        verify(refreshTokenMapper, never()).findByTokenHash(any());
        verify(refreshTokenMapper, never()).revokeAllByUserId(any(), any());
    }

    @Test
    @DisplayName("未知のトークンでのログアウトも例外にしない")
    void revokeIgnoresUnknownToken() {
        when(refreshTokenMapper.findByTokenHash(any())).thenReturn(Optional.empty());

        refreshTokenService.revoke("unknown");

        verify(refreshTokenMapper, never()).revokeAllByUserId(any(), any());
    }
}
