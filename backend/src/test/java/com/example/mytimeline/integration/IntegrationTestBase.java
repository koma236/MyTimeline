package com.example.mytimeline.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mytimeline.storage.S3StorageService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * API 結合テストの共通設定。Controller → Service → Mapper → H2 をモックなしで通す。
 *
 * <p>外部サービスの S3 だけは {@link MockitoBean} で差し替える。キーの採番と署名付き URL は
 * 決まった値を返すようにし、画像を伴うテストでも DB 側の検証ができるようにする。</p>
 *
 * <p>テストを {@code @Transactional} で包んでいないのは意図的。リフレッシュトークンの盗用検知は
 * {@code REQUIRES_NEW} の独立したトランザクションで全失効をコミットするが、テスト側のトランザクションに
 * 包むとその別トランザクションからは未コミットの行が見えず、検証にならない。
 * 代わりに各テスト後に users を全削除し、FK の CASCADE で関連行ごと掃除する。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestBase {

    protected static final String PASSWORD = "password123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    protected S3StorageService storageService;

    protected JdbcTemplate jdbc;

    /** サインアップ済みの利用者。以降のリクエストで必要な認証情報をまとめて持つ。 */
    protected record Session(long userId, String username, String accessToken, Cookie refreshCookie) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    @BeforeEach
    void setUpBase() {
        jdbc = new JdbcTemplate(dataSource);
        when(storageService.newPostImageKey(anyLong(), any())).thenReturn("posts/test/image.png");
        when(storageService.newAvatarKey(anyLong(), any())).thenReturn("avatars/test/avatar.png");
        when(storageService.presignedGetUrl(anyString()))
            .thenAnswer(invocation -> "https://s3.test/" + invocation.getArgument(0, String.class));
    }

    @AfterEach
    void cleanDatabase() {
        // posts / comments / likes / follows / refresh_tokens / post_images はすべて users への FK が CASCADE
        jdbc.update("DELETE FROM users");
    }

    /** サインアップして、アクセストークンとリフレッシュ Cookie を取り出す。 */
    protected Session signup(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupJson(username, username + "@example.com")))
            .andExpect(status().isCreated())
            .andReturn();
        String body = result.getResponse().getContentAsString();
        return new Session(
            ((Number) JsonPath.read(body, "$.user.id")).longValue(),
            username,
            JsonPath.read(body, "$.accessToken"),
            result.getResponse().getCookie("refreshToken")
        );
    }

    protected static String signupJson(String username, String email) {
        return "{\"username\":\"%s\",\"displayName\":\"%s さん\",\"email\":\"%s\",\"password\":\"%s\"}"
            .formatted(username, username, email, PASSWORD);
    }

    protected static String loginJson(String identifier, String password) {
        return "{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password);
    }

    /** 本文だけの投稿を作って id を返す。 */
    protected long createPost(Session session, String body) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/posts")
                .param("body", body)
                .header("Authorization", session.bearer()))
            .andExpect(status().isCreated())
            .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    /** ImageValidator を通る実物の PNG を multipart パートとして作る。 */
    protected static MockMultipartFile pngPart(String partName, String filename) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile(partName, filename, "image/png", out.toByteArray());
    }
}
