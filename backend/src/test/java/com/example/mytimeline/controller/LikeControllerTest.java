package com.example.mytimeline.controller;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mytimeline.config.SecurityConfig;
import com.example.mytimeline.dto.LikeResponse;
import com.example.mytimeline.exception.PostNotFoundException;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.security.JwtService;
import com.example.mytimeline.service.LikeService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LikeController.class)
@Import(SecurityConfig.class)
class LikeControllerTest {

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final Long CURRENT_USER_ID = 1L;
    private static final Long POST_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LikeService likeService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void authenticate() {
        when(jwtService.parseToken(VALID_TOKEN))
            .thenReturn(Optional.of(new CurrentUser(CURRENT_USER_ID, "taro")));
    }

    @Test
    @DisplayName("いいねの付与は 200 で件数といいね状態を返す")
    void likeReturnsCountAndState() throws Exception {
        when(likeService.like(POST_ID, CURRENT_USER_ID)).thenReturn(new LikeResponse(3L, true));

        mockMvc.perform(post("/api/posts/10/like").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.likeCount").value(3))
            .andExpect(jsonPath("$.likedByMe").value(true));
    }

    @Test
    @DisplayName("いいねの取り消しは 200 で件数と未いいね状態を返す")
    void unlikeReturnsCountAndState() throws Exception {
        when(likeService.unlike(POST_ID, CURRENT_USER_ID)).thenReturn(new LikeResponse(2L, false));

        mockMvc.perform(delete("/api/posts/10/like").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.likeCount").value(2))
            .andExpect(jsonPath("$.likedByMe").value(false));
    }

    @Test
    @DisplayName("同じ投稿へ二度いいねしても 200 のまま（冪等）")
    void likeIsIdempotent() throws Exception {
        when(likeService.like(POST_ID, CURRENT_USER_ID)).thenReturn(new LikeResponse(3L, true));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/posts/10/like").header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likedByMe").value(true));
        }

        verify(likeService, times(2)).like(POST_ID, CURRENT_USER_ID);
    }

    @Test
    @DisplayName("存在しない投稿へのいいねは 404 になる")
    void likeReturnsNotFound() throws Exception {
        when(likeService.like(999L, CURRENT_USER_ID)).thenThrow(new PostNotFoundException());

        mockMvc.perform(post("/api/posts/999/like").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value(PostNotFoundException.MESSAGE));
    }

    @Test
    @DisplayName("存在しない投稿のいいね取り消しは 404 になる")
    void unlikeReturnsNotFound() throws Exception {
        when(likeService.unlike(999L, CURRENT_USER_ID)).thenThrow(new PostNotFoundException());

        mockMvc.perform(delete("/api/posts/999/like").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("未認証のいいねは 401 になる")
    void likeRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/posts/10/like"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("ログインが必要です"));
    }
}
