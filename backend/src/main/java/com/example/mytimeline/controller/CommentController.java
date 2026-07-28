package com.example.mytimeline.controller;

import com.example.mytimeline.dto.CommentListResponse;
import com.example.mytimeline.dto.CommentRequest;
import com.example.mytimeline.dto.CommentResponse;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * コメントエンドポイント（docs/features/F04_comment.md 4. API エンドポイント案）。
 *
 * <p>一覧と作成は投稿に紐づくので {@code /api/posts/{postId}/comments}、
 * 編集と削除はコメント単体を指すので {@code /api/comments/{id}} に置いている。</p>
 *
 * <p>いずれも認証必須。{@code SecurityConfig} が permitAll を列挙した残りをすべて
 * {@code authenticated()} にしているため、ここに個別の設定は要らない。</p>
 */
@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * 投稿のコメントを古い順に取得する。
     *
     * <p>{@code cursor} は前回のレスポンスの {@code nextCursor} をそのまま渡す。
     * 省略すると先頭（最古）から返る。</p>
     */
    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<CommentListResponse> list(
        @PathVariable Long postId,
        @RequestParam(required = false) Long cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(commentService.list(postId, cursor, limit));
    }

    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> create(
        @PathVariable Long postId,
        @AuthenticationPrincipal CurrentUser currentUser,
        @Valid @RequestBody CommentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(commentService.create(postId, currentUser.id(), request));
    }

    /**
     * コメントの編集。コメント投稿者本人のみ（他人のコメントは 403、存在しなければ 404）。
     */
    @PutMapping("/api/comments/{id}")
    public ResponseEntity<CommentResponse> update(
        @PathVariable Long id,
        @AuthenticationPrincipal CurrentUser currentUser,
        @Valid @RequestBody CommentRequest request
    ) {
        return ResponseEntity.ok(commentService.update(id, currentUser.id(), request));
    }

    @DeleteMapping("/api/comments/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal CurrentUser currentUser
    ) {
        commentService.delete(id, currentUser.id());
        return ResponseEntity.noContent().build();
    }
}
