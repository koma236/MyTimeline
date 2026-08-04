package com.example.mytimeline.controller;

import com.example.mytimeline.dto.ProfileResponse;
import com.example.mytimeline.dto.TimelineResponse;
import com.example.mytimeline.dto.UpdateProfileRequest;
import com.example.mytimeline.dto.UserResponse;
import com.example.mytimeline.dto.UserSearchResponse;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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

    /** 検索キーワードの上限（F06 6.）。長すぎる入力は部分一致の相手として意味を成さない。 */
    private static final int MAX_QUERY_LENGTH = 50;

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * ユーザー検索（SCR-06・F06）。
     *
     * <p>{@code /api/users/{username}} より先に評価される。Spring は変数を含むパターンより
     * 固定の文字列を優先するため、並び順に関係なくこちらが選ばれる（その代わり
     * {@code search} という username のプロフィールは開けなくなる）。</p>
     *
     * @param q 検索キーワード。未指定・空なら新着ユーザーを返す
     */
    @GetMapping("/api/users/search")
    public ResponseEntity<UserSearchResponse> search(
        @AuthenticationPrincipal CurrentUser currentUser,
        @RequestParam(required = false)
        @Size(max = MAX_QUERY_LENGTH, message = "検索キーワードは50文字以内で入力してください")
        String q,
        @RequestParam(required = false) Long cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(userService.searchUsers(q, currentUser.id(), cursor, limit));
    }

    /**
     * プロフィールを取得する。
     *
     * <p>フォロー済みかどうか（{@code followingByMe}）を埋めるためにログイン中ユーザーを受け取る。
     * 見る人によって変わる値なので、対象ユーザーの情報だけでは決まらない。</p>
     */
    @GetMapping("/api/users/{username}")
    public ResponseEntity<ProfileResponse> getProfile(
        @PathVariable String username,
        @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(userService.getProfile(username, currentUser.id()));
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
