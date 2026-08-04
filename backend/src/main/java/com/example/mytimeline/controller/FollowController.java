package com.example.mytimeline.controller;

import com.example.mytimeline.dto.FollowResponse;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.service.FollowService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * フォローエンドポイント（docs/features/F06_follow.md 4. API エンドポイント案）。
 *
 * <p>{@link LikeController} と同じく、押すたびに反転する 1 本のトグルではなく登録（POST）と
 * 解除（DELETE）に分けている。トグルだと通信が再送されたときに意図と逆の状態になりうるが、
 * この形ならどちらを何度呼んでも結果は同じ（冪等）。</p>
 *
 * <p>参照系の {@code /api/users/{username}} と違い、対象を id で受け取る。フォローは
 * 「画面に出ているユーザー」に対する操作で、クライアントは必ずその id を持っているうえ、
 * username は改名で変わりうる（現状は変更できないが、経路を id に寄せておけば影響を受けない）。</p>
 *
 * <p>いずれも認証必須。{@code SecurityConfig} が permitAll を列挙した残りをすべて
 * {@code authenticated()} にしているため、ここに個別の設定は要らない。</p>
 */
@RestController
@RequestMapping("/api/users/{userId}/follow")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping
    public ResponseEntity<FollowResponse> follow(
        @PathVariable Long userId,
        @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(followService.follow(userId, currentUser.id()));
    }

    @DeleteMapping
    public ResponseEntity<FollowResponse> unfollow(
        @PathVariable Long userId,
        @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(followService.unfollow(userId, currentUser.id()));
    }
}
