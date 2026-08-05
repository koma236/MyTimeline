package com.example.mytimeline.controller;

import com.example.mytimeline.dto.PostRequest;
import com.example.mytimeline.dto.PostResponse;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.service.PostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 投稿エンドポイント（docs/features/F03_post.md 4. API エンドポイント）。
 *
 * <p>いずれも認証必須。{@code SecurityConfig} が permitAll を列挙した残りをすべて
 * {@code authenticated()} にしているため、ここに個別の設定は要らない。</p>
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 投稿を作成する。画像を添付できるように multipart で受ける（F03）。
     *
     * <p>画像なしの投稿も同じ形式で受ける。JSON と multipart の 2 経路を持つと
     * バリデーションが二重になるため、作成はこの 1 経路に揃えている。
     * 本文と画像は独立した入力なので、JSON パートではなく素のフィールドで受ける
     * （クライアントは FormData に詰めるだけでよい）。</p>
     *
     * <p>「本文が空かつ画像も無い場合は投稿不可」は 2 つの入力にまたがる制約のため、
     * アノテーションではなくサービス側で検証する。</p>
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> create(
        @AuthenticationPrincipal CurrentUser currentUser,
        @RequestParam(value = "body", required = false)
        @Size(max = PostRequest.BODY_MAX_LENGTH, message = "本文は280文字以内で入力してください")
        String body,
        @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(postService.create(currentUser.id(), body, images));
    }

    /**
     * 投稿を 1 件取得する。
     *
     * <p>ログインユーザーを渡すのは、レスポンスの {@code likedByMe}（自分がいいね済みか）を
     * 判定するため。認証必須なので principal は必ず入る。</p>
     */
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getById(
        @PathVariable Long id,
        @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(postService.getById(id, currentUser.id()));
    }

    /**
     * 投稿の編集。投稿者本人のみ（他人の投稿は 403、存在しなければ 404）。
     */
    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> update(
        @PathVariable Long id,
        @AuthenticationPrincipal CurrentUser currentUser,
        @Valid @RequestBody PostRequest request
    ) {
        return ResponseEntity.ok(postService.update(id, currentUser.id(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal CurrentUser currentUser
    ) {
        postService.delete(id, currentUser.id());
        return ResponseEntity.noContent().build();
    }
}
