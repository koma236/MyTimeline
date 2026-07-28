package com.example.mytimeline.controller;

import com.example.mytimeline.dto.TimelineResponse;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * タイムラインエンドポイント（docs/features/F02_timeline.md 4. API エンドポイント案）。
 *
 * <p>{@code cursor} は前回のレスポンスの {@code nextCursor} をそのまま渡す。
 * 省略すると先頭（最新）から返る。不正な値でも例外にはせず、単に
 * 「その id より古い投稿」が 0 件返るだけなので、クライアントは先頭から取り直せる。</p>
 */
@RestController
@RequestMapping("/api/timeline")
public class TimelineController {

    private final PostService postService;

    public TimelineController(PostService postService) {
        this.postService = postService;
    }

    /** フォロー中タイムライン（自分＋フォロー先。フォローは F06 で実装）。 */
    @GetMapping("/following")
    public ResponseEntity<TimelineResponse> following(
        @AuthenticationPrincipal CurrentUser currentUser,
        @RequestParam(required = false) Long cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(postService.getFollowingTimeline(currentUser.id(), cursor, limit));
    }

    /**
     * 全体タイムライン。
     *
     * <p>絞り込みにログインユーザーは使わないが、各投稿の {@code likedByMe}
     * （自分がいいね済みか）を判定するために principal を受け取る。</p>
     */
    @GetMapping("/all")
    public ResponseEntity<TimelineResponse> all(
        @AuthenticationPrincipal CurrentUser currentUser,
        @RequestParam(required = false) Long cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(postService.getAllTimeline(currentUser.id(), cursor, limit));
    }
}
