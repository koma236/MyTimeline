package com.example.mytimeline.controller;

import com.example.mytimeline.dto.LikeResponse;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.service.LikeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * いいねエンドポイント（docs/features/F05_like.md 4. API エンドポイント案）。
 *
 * <p>「押すたびに反転」する 1 本のトグルではなく、付与（POST）と取り消し（DELETE）を
 * 分けている。トグルだと通信が再送されたときに意図と逆の状態になりうるが、
 * この形ならどちらを何度呼んでも結果は同じ（冪等）。</p>
 *
 * <p>いずれも認証必須。{@code SecurityConfig} が permitAll を列挙した残りをすべて
 * {@code authenticated()} にしているため、ここに個別の設定は要らない。</p>
 */
@RestController
@RequestMapping("/api/posts/{postId}/like")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping
    public ResponseEntity<LikeResponse> like(
        @PathVariable Long postId,
        @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(likeService.like(postId, currentUser.id()));
    }

    @DeleteMapping
    public ResponseEntity<LikeResponse> unlike(
        @PathVariable Long postId,
        @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(likeService.unlike(postId, currentUser.id()));
    }
}
