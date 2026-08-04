package com.example.mytimeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mytimeline.dto.ProfileResponse;
import com.example.mytimeline.dto.UpdateProfileRequest;
import com.example.mytimeline.dto.UserResponse;
import com.example.mytimeline.exception.ProfileNotFoundException;
import com.example.mytimeline.mapper.UserMapper;
import com.example.mytimeline.model.User;
import com.example.mytimeline.storage.AvatarUrlFactory;
import com.example.mytimeline.storage.ImageValidator;
import com.example.mytimeline.storage.InvalidImageException;
import com.example.mytimeline.storage.S3StorageService;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final String USERNAME = "taro";

    @Mock
    private UserMapper userMapper;

    @Mock
    private PostService postService;

    @Mock
    private S3StorageService storageService;

    @Mock
    private ImageValidator imageValidator;

    @Mock
    private AvatarUrlFactory avatarUrlFactory;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("プロフィールを username で取得できる")
    void getProfileReturnsUser() {
        User user = user("既存の自己紹介", "avatars/1/old.png");
        when(userMapper.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(avatarUrlFactory.urlFor("avatars/1/old.png")).thenReturn("https://example.com/signed");

        ProfileResponse response = userService.getProfile(USERNAME);

        assertThat(response.username()).isEqualTo(USERNAME);
        assertThat(response.bio()).isEqualTo("既存の自己紹介");
        assertThat(response.avatarUrl()).isEqualTo("https://example.com/signed");
    }

    @Test
    @DisplayName("存在しない username のプロフィールは 404 相当になる")
    void getProfileThrowsWhenMissing() {
        when(userMapper.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile("unknown"))
            .isInstanceOf(ProfileNotFoundException.class)
            .hasMessage(ProfileNotFoundException.MESSAGE);
    }

    @Test
    @DisplayName("存在しない username の投稿一覧は 404 相当になり、投稿の取得も実行されない")
    void getUserPostsThrowsBeforeFetchingPosts() {
        // 空配列を返すと「そんなユーザーはいない」と「まだ投稿していない」を画面が区別できない
        when(userMapper.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserPosts("unknown", USER_ID, null, null))
            .isInstanceOf(ProfileNotFoundException.class);

        verify(postService, never()).getUserTimeline(any(), any(), any(), any());
    }

    @Test
    @DisplayName("投稿一覧は対象ユーザーの id で取得する")
    void getUserPostsDelegatesWithAuthorId() {
        when(userMapper.findByUsername(USERNAME)).thenReturn(Optional.of(user(null, null)));

        userService.getUserPosts(USERNAME, 99L, 5L, 10);

        verify(postService).getUserTimeline(USER_ID, 99L, 5L, 10);
    }

    @Test
    @DisplayName("表示名と自己紹介を更新できる")
    void updateProfileUpdatesFields() {
        when(userMapper.findById(USER_ID)).thenReturn(Optional.of(user("新しい自己紹介", null)));

        UserResponse response = userService.updateProfile(
            USER_ID, new UpdateProfileRequest("新しい表示名", "新しい自己紹介")
        );

        verify(userMapper).updateProfile(USER_ID, "新しい表示名", "新しい自己紹介");
        assertThat(response.bio()).isEqualTo("新しい自己紹介");
    }

    @Test
    @DisplayName("空白だけの自己紹介は null として保存する")
    void updateProfileNormalizesBlankBioToNull() {
        // DB のコメントが「未設定は NULL」と宣言している以上、'' と NULL の
        // 2 通りの未設定を混在させない
        when(userMapper.findById(USER_ID)).thenReturn(Optional.of(user(null, null)));

        userService.updateProfile(USER_ID, new UpdateProfileRequest("山田太郎", "   "));

        verify(userMapper).updateProfile(USER_ID, "山田太郎", null);
    }

    @Test
    @DisplayName("アバターは「保存 → DB 更新 → 旧画像の削除」の順で処理する")
    void updateAvatarStoresBeforeUpdatingDatabase() throws IOException {
        MultipartFile file = pngFile();
        when(imageValidator.validate(file)).thenReturn(ImageValidator.ImageFormat.PNG);
        when(userMapper.findById(USER_ID)).thenReturn(Optional.of(user(null, "avatars/1/old.png")));
        when(storageService.newAvatarKey(USER_ID, ImageValidator.ImageFormat.PNG))
            .thenReturn("avatars/1/new.png");

        userService.updateAvatar(USER_ID, file);

        // 逆順だと途中で失敗したときに「DB はキーを指すのに実体が無い」状態になる
        InOrder order = inOrder(storageService, userMapper);
        order.verify(storageService).put(eq("avatars/1/new.png"), any(byte[].class), anyString());
        order.verify(userMapper).updateAvatarKey(USER_ID, "avatars/1/new.png");
        order.verify(storageService).deleteQuietly("avatars/1/old.png");
    }

    @Test
    @DisplayName("アバターが未設定なら旧画像の削除は行わない")
    void updateAvatarSkipsDeleteWhenNoPreviousKey() throws IOException {
        MultipartFile file = pngFile();
        when(imageValidator.validate(file)).thenReturn(ImageValidator.ImageFormat.PNG);
        when(userMapper.findById(USER_ID)).thenReturn(Optional.of(user(null, null)));
        when(storageService.newAvatarKey(USER_ID, ImageValidator.ImageFormat.PNG))
            .thenReturn("avatars/1/new.png");

        userService.updateAvatar(USER_ID, file);

        verify(storageService, never()).deleteQuietly(any());
    }

    @Test
    @DisplayName("検証に失敗した画像はストレージに触れない")
    void updateAvatarDoesNotTouchStorageWhenInvalid() {
        MultipartFile file = pngFile();
        when(imageValidator.validate(file))
            .thenThrow(new InvalidImageException(InvalidImageException.UNSUPPORTED_FORMAT));

        assertThatThrownBy(() -> userService.updateAvatar(USER_ID, file))
            .isInstanceOf(InvalidImageException.class);

        verify(storageService, never()).newAvatarKey(any(), any());
        verify(userMapper, never()).updateAvatarKey(any(), any());
    }

    @Test
    @DisplayName("アバターを削除するとキーが null になり、実体も消される")
    void deleteAvatarClearsKeyAndObject() {
        when(userMapper.findById(USER_ID)).thenReturn(Optional.of(user(null, "avatars/1/old.png")));

        userService.deleteAvatar(USER_ID);

        verify(userMapper).updateAvatarKey(USER_ID, null);
        verify(storageService).deleteQuietly("avatars/1/old.png");
    }

    @Test
    @DisplayName("アバターが未設定のまま削除しても何も起きない（冪等）")
    void deleteAvatarIsIdempotent() {
        when(userMapper.findById(USER_ID)).thenReturn(Optional.of(user(null, null)));

        userService.deleteAvatar(USER_ID);

        verify(userMapper, never()).updateAvatarKey(any(), any());
        verify(storageService, never()).deleteQuietly(any());
    }

    private static MultipartFile pngFile() {
        return new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2, 3});
    }

    private static User user(String bio, String avatarKey) {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername(USERNAME);
        user.setDisplayName("山田太郎");
        user.setEmail("taro@example.com");
        user.setBio(bio);
        user.setAvatarKey(avatarKey);
        return user;
    }
}
