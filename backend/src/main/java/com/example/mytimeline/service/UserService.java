package com.example.mytimeline.service;

import com.example.mytimeline.dto.ProfileResponse;
import com.example.mytimeline.dto.TimelineResponse;
import com.example.mytimeline.dto.UpdateProfileRequest;
import com.example.mytimeline.dto.UserResponse;
import com.example.mytimeline.exception.ProfileNotFoundException;
import com.example.mytimeline.exception.UserNotFoundException;
import com.example.mytimeline.mapper.UserMapper;
import com.example.mytimeline.model.User;
import com.example.mytimeline.storage.AvatarUrlFactory;
import com.example.mytimeline.storage.ImageValidator;
import com.example.mytimeline.storage.S3StorageService;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * プロフィールの参照と編集（docs/features/F07_profile.md）。
 *
 * <p>編集系はすべて「ログイン中の本人」に対する操作で、対象ユーザーを ID で受け取らない。
 * エンドポイントも {@code /api/users/me} に固定してあるため、他人のプロフィールを
 * 書き換える経路自体が存在しない。{@link PostService} のような所有者チェック（403）が
 * ここに無いのはそのため。</p>
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserMapper userMapper;
    private final PostService postService;
    private final S3StorageService storageService;
    private final ImageValidator imageValidator;
    private final AvatarUrlFactory avatarUrlFactory;

    public UserService(
        UserMapper userMapper,
        PostService postService,
        S3StorageService storageService,
        ImageValidator imageValidator,
        AvatarUrlFactory avatarUrlFactory
    ) {
        this.userMapper = userMapper;
        this.postService = postService;
        this.storageService = storageService;
        this.imageValidator = imageValidator;
        this.avatarUrlFactory = avatarUrlFactory;
    }

    /**
     * username でプロフィールを取得する（SCR-05）。
     */
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String username) {
        User user = findByUsernameOrThrow(username);
        return ProfileResponse.from(user, avatarUrlFactory.urlFor(user.getAvatarKey()));
    }

    /**
     * 対象ユーザーの投稿一覧（SCR-05 の投稿一覧）。
     *
     * <p>存在しない username では空配列ではなく 404 を返す。空配列だと画面が
     * 「そんなユーザーはいない」と「まだ投稿していない」を区別できないため。</p>
     */
    @Transactional(readOnly = true)
    public TimelineResponse getUserPosts(String username, Long currentUserId, Long cursor, Integer limit) {
        User user = findByUsernameOrThrow(username);
        return postService.getUserTimeline(user.getId(), currentUserId, cursor, limit);
    }

    /**
     * 表示名と自己紹介を更新する。
     *
     * <p>更新後に読み直しているのは、DB 側で進んだ {@code updated_at} を含む
     * 完全なレスポンスを返すため（{@link PostService#update} と同じ）。</p>
     */
    @Transactional
    public UserResponse updateProfile(Long currentUserId, UpdateProfileRequest request) {
        userMapper.updateProfile(currentUserId, request.displayName(), normalizeBio(request.bio()));
        log.info("プロフィールを更新しました: userId={}", currentUserId);

        return toUserResponse(findByIdOrThrow(currentUserId));
    }

    /**
     * アバター画像を差し替える。
     *
     * <p>「先に保存 → DB を更新 → 古い画像を削除」の順序が重要。逆にすると途中で失敗したときに
     * 「DB はキーを指しているのに実体が無い」という壊れ方をする。この順序なら最悪でも
     * 参照されないオブジェクトが 1 個残るだけで、画面の見え方は変わらない。</p>
     *
     * <p>ストレージへの操作はトランザクションでロールバックできないため、孤児の発生は
     * 許容する。数が問題になるようなら定期的な掃除で回収する（F07 の課題として記載）。</p>
     */
    @Transactional
    public UserResponse updateAvatar(Long currentUserId, MultipartFile file) {
        ImageValidator.ImageFormat format = imageValidator.validate(file);

        User user = findByIdOrThrow(currentUserId);
        String previousKey = user.getAvatarKey();
        String newKey = storageService.newAvatarKey(currentUserId, format);

        try {
            storageService.put(newKey, file.getBytes(), format.contentType());
        } catch (IOException e) {
            throw new AvatarUploadException(e);
        }

        userMapper.updateAvatarKey(currentUserId, newKey);
        log.info("アバターを更新しました: userId={}, key={}", currentUserId, newKey);

        deletePreviousAvatar(previousKey);
        return toUserResponse(findByIdOrThrow(currentUserId));
    }

    /**
     * アバター画像を外し、初期アバター（表示名の頭文字）に戻す。
     *
     * <p>「空のファイルを送る＝削除」にせず独立したエンドポイントにしている。
     * 前者だと通信事故で中身が空になったのか意図的な削除なのかを区別できない。</p>
     *
     * <p>アバターが未設定の状態で呼ばれても何もせず成功として扱う（冪等）。</p>
     */
    @Transactional
    public UserResponse deleteAvatar(Long currentUserId) {
        User user = findByIdOrThrow(currentUserId);
        String previousKey = user.getAvatarKey();

        if (previousKey != null) {
            userMapper.updateAvatarKey(currentUserId, null);
            log.info("アバターを削除しました: userId={}", currentUserId);
            deletePreviousAvatar(previousKey);
        }

        return toUserResponse(findByIdOrThrow(currentUserId));
    }

    /**
     * 空文字や空白だけの自己紹介は null に寄せる。
     *
     * <p>DB のコメントで「未設定は NULL」と宣言している以上、'' と NULL の 2 通りの
     * 「未設定」が混ざると、後から条件を書くたびに両方を気にすることになる。</p>
     */
    private String normalizeBio(String bio) {
        if (bio == null || bio.isBlank()) {
            return null;
        }
        return bio;
    }

    private void deletePreviousAvatar(String previousKey) {
        if (previousKey != null) {
            storageService.deleteQuietly(previousKey);
        }
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.from(user, avatarUrlFactory.urlFor(user.getAvatarKey()));
    }

    private User findByUsernameOrThrow(String username) {
        return userMapper.findByUsername(username).orElseThrow(ProfileNotFoundException::new);
    }

    /**
     * ログイン中ユーザーを取得する。
     *
     * <p>トークンは有効なのにレコードが無いのは想定外の状態（削除直後など）。
     * 他人を探した結果の 404 とは別物なので {@link ProfileNotFoundException} は使わない。</p>
     */
    private User findByIdOrThrow(Long userId) {
        return userMapper.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    /**
     * アップロードされたファイルが読めなかった場合。
     *
     * <p>クライアントの入力ミスではなく通信・サーバー側の問題なので、
     * 個別のハンドラを用意せず {@code GlobalExceptionHandler} の 500 に流す。</p>
     */
    static class AvatarUploadException extends RuntimeException {
        AvatarUploadException(Throwable cause) {
            super("アバター画像のアップロードに失敗しました", cause);
        }
    }
}
