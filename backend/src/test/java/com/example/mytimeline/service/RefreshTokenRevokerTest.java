package com.example.mytimeline.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.mytimeline.mapper.RefreshTokenMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RefreshTokenRevoker} は Mapper への委譲だけを行う。
 *
 * <p>このクラスの本質である「呼び出し元とは独立したトランザクションでコミットされる」性質は
 * モックでは検証できないため、結合テスト（AuthFlowIntegrationTest の盗用検知）で確かめる。
 * ここでは引数がそのまま渡ることだけを見る。</p>
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenRevokerTest {

    @Mock
    private RefreshTokenMapper refreshTokenMapper;

    @InjectMocks
    private RefreshTokenRevoker revoker;

    @Test
    @DisplayName("ユーザー id と失効時刻をそのまま Mapper に渡す")
    void delegatesToMapper() {
        LocalDateTime revokedAt = LocalDateTime.of(2026, 8, 22, 12, 0);

        revoker.revokeAllByUserId(42L, revokedAt);

        verify(refreshTokenMapper).revokeAllByUserId(42L, revokedAt);
        verifyNoMoreInteractions(refreshTokenMapper);
    }
}
