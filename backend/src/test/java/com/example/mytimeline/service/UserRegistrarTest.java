package com.example.mytimeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mytimeline.mapper.UserMapper;
import com.example.mytimeline.model.User;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserRegistrar} の処理の順序と戻り値を確かめる。
 *
 * <p>このクラスの本質である「INSERT とリフレッシュトークン発行が 1 つのトランザクションで
 * コミット・ロールバックされる」性質はモックでは検証できないため、結合テスト
 * （AuthTransactionBoundaryIntegrationTest）で確かめる。</p>
 *
 * <p>設計技法: 分岐網羅。分岐は「読み直しでユーザーが見つかる / 見つからない」の 1 つ。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserRegistrarTest {

    private static final String ISSUED_REFRESH_TOKEN = "issued-refresh-token";

    @Mock
    private UserMapper userMapper;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserRegistrar registrar;

    private User newUser() {
        User user = new User();
        user.setUsername("taro");
        return user;
    }

    /** MyBatis の useGeneratedKeys と同じく、INSERT で引数のオブジェクトに id が入る動きを再現する。 */
    private void insertAssignsId(long id) {
        doAnswer(invocation -> {
            invocation.getArgument(0, User.class).setId(id);
            return null;
        }).when(userMapper).insert(any());
    }

    @Test
    @DisplayName("分岐網羅（見つかる）: INSERT → 読み直し → リフレッシュトークン発行の順に実行し、読み直した値を返す")
    void insertsThenReloadsThenIssuesRefreshToken() {
        User reloaded = newUser();
        reloaded.setId(7L);
        insertAssignsId(7L);
        when(userMapper.findById(7L)).thenReturn(Optional.of(reloaded));
        when(refreshTokenService.issue(7L)).thenReturn(ISSUED_REFRESH_TOKEN);

        UserRegistrar.Registration registration = registrar.register(newUser());

        // created_at など DB 側で入る値を返すため、引数ではなく読み直したオブジェクトであること
        assertThat(registration.user()).isSameAs(reloaded);
        assertThat(registration.rawRefreshToken()).isEqualTo(ISSUED_REFRESH_TOKEN);

        InOrder order = inOrder(userMapper, refreshTokenService);
        order.verify(userMapper).insert(any());
        order.verify(userMapper).findById(7L);
        order.verify(refreshTokenService).issue(7L);
    }

    @Test
    @DisplayName("分岐網羅（見つからない）: 読み直しで見つからなければ例外にし、リフレッシュトークンは発行しない")
    void failsWithoutIssuingWhenReloadFindsNothing() {
        insertAssignsId(7L);
        when(userMapper.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrar.register(newUser()))
            .isInstanceOf(NoSuchElementException.class);

        verify(refreshTokenService, never()).issue(any());
    }
}
