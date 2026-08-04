package com.example.mytimeline.controller;

import com.example.mytimeline.dto.ProfileResponse;
import com.example.mytimeline.dto.TimelineResponse;
import com.example.mytimeline.dto.UpdateProfileRequest;
import com.example.mytimeline.dto.UserResponse;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * プロフィールエンドポイント（docs/features/F07_profile.md 4. API エンドポイント）。
 *
 * <p>参照は username で引く（{@code /api/users/{username}}）。プロフィールの URL は
 * 人が見て分かる方がよく、画面上のリンク（SCR-05）とも一致する。</p>
 *
 * <p>編集系は {@code {id}} を受けず {@code /me} に固定している。プロフィールは本人しか
 * 編集できないので ID を受け取る意味がなく、受け取れば必ず所有者チェックが要る。
 * パスの設計でその分岐ごと無くしている。</p>
 *
 * <p>いずれも認証必須。{@code SecurityConfig} が permitAll を列挙した残りをすべて
 * {@code authenticated()} にしているため、ここに個別の設定は要らない。</p>
 */
@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/users/{username}")
    public ResponseEntity<ProfileResponse> getProfile(@PathVariable String username) {
        return ResponseEntity.ok(userService.getProfile(username));
    }

    /**
     * 対象ユーザーの投稿を新しい順に取得する。
     *
     * <p>{@code cursor} は前回のレスポンスの {@code nextCursor} をそのまま渡す。</p>
     */
    @GetMapping("/api/users/{username}/posts")
    public ResponseEntity<TimelineResponse> getUserPosts(
        @PathVariable String username,
        @AuthenticationPrincipal CurrentUser currentUser,
        @RequestParam(required = false) Long cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(userService.getUserPosts(username, currentUser.id(), cursor, limit));
    }

    /**
     * 表示名と自己紹介を更新する。
     *
     * <p>メールアドレスを含む {@link UserResponse} を返すが、本人のみが叩けるので問題ない。
     * クライアントはこれをそのままログイン中ユーザーの状態に反映できる。</p>
     */
    @PutMapping("/api/users/me")
    public ResponseEntity<UserResponse> updateProfile(
        @AuthenticationPrincipal CurrentUser currentUser,
        @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(userService.updateProfile(currentUser.id(), request));
    }

    /**
     * アバター画像を差し替える。
     *
     * <p>画像の検証はすべてサーバー側で行う（{@code ImageValidator}）。
     * 申告された Content-Type やファイル名の拡張子は信用しない。</p>
     */
    @PutMapping("/api/users/me/avatar")
    public ResponseEntity<UserResponse> updateAvatar(
        @AuthenticationPrincipal CurrentUser currentUser,
        @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(userService.updateAvatar(currentUser.id(), file));
    }

    /**
     * アバター画像を外して初期アバターに戻す。
     *
     * <p>204 ではなく更新後の {@link UserResponse} を返す。クライアントは戻り値を
     * そのままログイン中ユーザーの状態へ流し込めば済み、再取得が要らない
     * （{@code LikeController} が操作後の状態を返しているのと同じ考え方）。</p>
     */
    @DeleteMapping("/api/users/me/avatar")
    public ResponseEntity<UserResponse> deleteAvatar(@AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(userService.deleteAvatar(currentUser.id()));
    }
}
