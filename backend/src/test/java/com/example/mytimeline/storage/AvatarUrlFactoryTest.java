package com.example.mytimeline.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 設計技法: 同値分割（キーが null / 非 null）。
 * null のときにストレージへ問い合わせないことが、このクラスの存在理由。
 */
@ExtendWith(MockitoExtension.class)
class AvatarUrlFactoryTest {

    @Mock
    private S3StorageService storageService;

    @InjectMocks
    private AvatarUrlFactory factory;

    @Test
    @DisplayName("キーが null なら URL も null で、ストレージには問い合わせない")
    void nullKeyYieldsNullWithoutStorageAccess() {
        assertThat(factory.urlFor(null)).isNull();

        verify(storageService, never()).presignedGetUrl(anyString());
    }

    @Test
    @DisplayName("キーがあれば署名付き URL の生成に委譲する")
    void keyIsDelegatedToPresigner() {
        when(storageService.presignedGetUrl("avatars/1/a.png")).thenReturn("https://s3/avatars/1/a.png?sig");

        assertThat(factory.urlFor("avatars/1/a.png")).isEqualTo("https://s3/avatars/1/a.png?sig");
    }
}
