package com.example.mytimeline.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mytimeline.config.SecurityConfig;
import com.example.mytimeline.dto.PostAuthor;
import com.example.mytimeline.dto.PostResponse;
import com.example.mytimeline.dto.TimelineResponse;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.security.JwtService;
import com.example.mytimeline.service.PostService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TimelineController.class)
@Import(SecurityConfig.class)
class TimelineControllerTest {

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final Long CURRENT_USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void authenticate() {
        when(jwtService.parseToken(VALID_TOKEN))
            .thenReturn(Optional.of(new CurrentUser(CURRENT_USER_ID, "taro")));
    }

    private static TimelineResponse timeline(Long nextCursor) {
        PostResponse post = new PostResponse(
            10L,
            "こんにちは",
            new PostAuthor(CURRENT_USER_ID, "taro", "山田太郎", null),
            5L,
            1L,
            false,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        return new TimelineResponse(List.of(post), nextCursor);
    }

    @Test
    @DisplayName("全体タイムラインは投稿と次のカーソルを返す")
    void allReturnsTimeline() throws Exception {
        when(postService.getAllTimeline(CURRENT_USER_ID, null, null)).thenReturn(timeline(9L));

        mockMvc.perform(get("/api/timeline/all").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.posts[0].id").value(10))
            .andExpect(jsonPath("$.posts[0].author.displayName").value("山田太郎"))
            .andExpect(jsonPath("$.nextCursor").value(9));
    }

    @Test
    @DisplayName("全体タイムラインの各投稿にいいね数・コメント数・自分のいいね状態が載る")
    void allReturnsCounts() throws Exception {
        when(postService.getAllTimeline(CURRENT_USER_ID, null, null)).thenReturn(timeline(null));

        mockMvc.perform(get("/api/timeline/all").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.posts[0].likeCount").value(5))
            .andExpect(jsonPath("$.posts[0].commentCount").value(1))
            .andExpect(jsonPath("$.posts[0].likedByMe").value(false));
    }

    @Test
    @DisplayName("cursor と limit はそのままサービスへ渡される")
    void allPassesCursorAndLimit() throws Exception {
        when(postService.getAllTimeline(CURRENT_USER_ID, 50L, 10)).thenReturn(timeline(null));

        mockMvc.perform(get("/api/timeline/all")
                .param("cursor", "50")
                .param("limit", "10")
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk());

        verify(postService).getAllTimeline(CURRENT_USER_ID, 50L, 10);
    }

    @Test
    @DisplayName("最終ページでは nextCursor が null になる")
    void allReturnsNullCursorOnLastPage() throws Exception {
        when(postService.getAllTimeline(CURRENT_USER_ID, null, null)).thenReturn(timeline(null));

        mockMvc.perform(get("/api/timeline/all").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("フォロー中タイムラインはログインユーザーの id を渡して取得する")
    void followingUsesCurrentUser() throws Exception {
        when(postService.getFollowingTimeline(CURRENT_USER_ID, null, null)).thenReturn(timeline(null));

        mockMvc.perform(get("/api/timeline/following").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.posts[0].id").value(10));

        verify(postService).getFollowingTimeline(CURRENT_USER_ID, null, null);
    }

    @Test
    @DisplayName("未認証のタイムライン取得は 401 になる")
    void timelineRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/timeline/all"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("ログインが必要です"));
    }
}
