package com.example.mytimeline.controller;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mytimeline.config.SecurityConfig;
import com.example.mytimeline.dto.FollowResponse;
import com.example.mytimeline.exception.ProfileNotFoundException;
import com.example.mytimeline.exception.SelfFollowException;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.security.JwtService;
import com.example.mytimeline.service.FollowService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FollowController.class)
@Import(SecurityConfig.class)
class FollowControllerTest {

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final Long CURRENT_USER_ID = 1L;
    private static final Long TARGET_ID = 2L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FollowService followService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void authenticate() {
        when(jwtService.parseToken(VALID_TOKEN))
            .thenReturn(Optional.of(new CurrentUser(CURRENT_USER_ID, "taro")));
    }

    @Test
    @DisplayName("フォローは 200 でフォロワー数とフォロー状態を返す")
    void followReturnsCountAndState() throws Exception {
        when(followService.follow(TARGET_ID, CURRENT_USER_ID)).thenReturn(new FollowResponse(3L, true));

        mockMvc.perform(post("/api/users/2/follow").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.followerCount").value(3))
            .andExpect(jsonPath("$.followingByMe").value(true));
    }

    @Test
    @DisplayName("フォロー解除は 200 でフォロワー数と未フォロー状態を返す")
    void unfollowReturnsCountAndState() throws Exception {
        when(followService.unfollow(TARGET_ID, CURRENT_USER_ID)).thenReturn(new FollowResponse(2L, false));

        mockMvc.perform(delete("/api/users/2/follow").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.followerCount").value(2))
            .andExpect(jsonPath("$.followingByMe").value(false));
    }

    @Test
    @DisplayName("同じ相手を二度フォローしても 200 のまま（冪等）")
    void followIsIdempotent() throws Exception {
        when(followService.follow(TARGET_ID, CURRENT_USER_ID)).thenReturn(new FollowResponse(3L, true));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/users/2/follow").header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followingByMe").value(true));
        }

        verify(followService, times(2)).follow(TARGET_ID, CURRENT_USER_ID);
    }

    @Test
    @DisplayName("自分自身のフォローは 400 になる")
    void followRejectsSelf() throws Exception {
        when(followService.follow(CURRENT_USER_ID, CURRENT_USER_ID)).thenThrow(new SelfFollowException());

        mockMvc.perform(post("/api/users/1/follow").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(SelfFollowException.MESSAGE));
    }

    @Test
    @DisplayName("存在しないユーザーへのフォローは 404 になる")
    void followReturnsNotFound() throws Exception {
        when(followService.follow(999L, CURRENT_USER_ID)).thenThrow(new ProfileNotFoundException());

        mockMvc.perform(post("/api/users/999/follow").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value(ProfileNotFoundException.MESSAGE));
    }

    @Test
    @DisplayName("存在しないユーザーのフォロー解除は 404 になる")
    void unfollowReturnsNotFound() throws Exception {
        when(followService.unfollow(999L, CURRENT_USER_ID)).thenThrow(new ProfileNotFoundException());

        mockMvc.perform(delete("/api/users/999/follow").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("id が数値でないフォローは 500 ではなく 400 になる")
    void followRejectsNonNumericId() throws Exception {
        mockMvc.perform(post("/api/users/abc/follow").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("リクエストの形式が正しくありません"));
    }

    @Test
    @DisplayName("未認証のフォローは 401 になる")
    void followRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/users/2/follow"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("ログインが必要です"));
    }
}
