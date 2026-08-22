package com.example.mytimeline.exception;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mytimeline.config.SecurityConfig;
import com.example.mytimeline.security.CurrentUser;
import com.example.mytimeline.security.JwtService;
import com.example.mytimeline.storage.InvalidImageException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

/**
 * 例外 → 共通エラー形式（{@code message} / {@code fieldErrors}）への変換を検証する。
 *
 * <p>設計技法: 同値分割。ハンドラが受ける例外を「業務例外（ステータスごと）」
 * 「フレームワーク例外（入力の解釈失敗）」「想定外の例外」に分け、各クラスから代表を 1 つずつ投げる。
 * 実際の Controller は Service をモックしないと目的の例外を起こせないため、
 * テスト専用の小さな Controller から直接投げる。</p>
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ThrowingController.class)
@Import({SecurityConfig.class, GlobalExceptionHandlerTest.ThrowingController.class})
class GlobalExceptionHandlerTest {

    private static final String VALID_TOKEN = "valid.jwt.token";

    private static final Map<String, Supplier<RuntimeException>> BUSINESS_EXCEPTIONS = Map.ofEntries(
        Map.entry("invalidCredentials", InvalidCredentialsException::new),
        Map.entry("userNotFound", UserNotFoundException::new),
        Map.entry("invalidRefreshToken", InvalidRefreshTokenException::new),
        Map.entry("postNotFound", PostNotFoundException::new),
        Map.entry("postForbidden", PostForbiddenException::new),
        Map.entry("commentNotFound", CommentNotFoundException::new),
        Map.entry("commentForbidden", CommentForbiddenException::new),
        Map.entry("profileNotFound", ProfileNotFoundException::new),
        Map.entry("selfFollow", SelfFollowException::new),
        Map.entry("emptyPost", EmptyPostException::new),
        Map.entry("duplicateField", () -> new DuplicateFieldException("email", "このメールアドレスは既に使用されています")),
        Map.entry("duplicateKey", () -> new DuplicateKeyException("uq_users_email")),
        Map.entry("invalidImage", () -> new InvalidImageException("画像は JPEG か PNG にしてください")),
        Map.entry("maxUpload", () -> new MaxUploadSizeExceededException(1)),
        Map.entry("unexpected", () -> new IllegalStateException("secret internal detail"))
    );

    /** 例外を起こすためだけの Controller。 */
    @RestController
    @RequestMapping("/test")
    static class ThrowingController {

        record ValidatedBody(
            @NotBlank(message = "名前を入力してください")
            @Size(min = 3, message = "名前は3文字以上にしてください")
            String name,
            @Size(max = 2, message = "タグは2文字以内にしてください")
            String tag
        ) {
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody ValidatedBody body) {
        }

        @GetMapping("/param")
        void param(@RequestParam @Size(max = 3, message = "q は3文字以内にしてください") String q) {
        }

        @GetMapping("/typed/{id}")
        void typed(@PathVariable Long id) {
        }

        @PostMapping(value = "/part", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        void part(@RequestPart("file") MultipartFile file) {
        }

        @GetMapping("/throw/{name}")
        void doThrow(@PathVariable String name) {
            throw BUSINESS_EXCEPTIONS.get(name).get();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void authenticate() {
        when(jwtService.parseToken(VALID_TOKEN)).thenReturn(Optional.of(new CurrentUser(1L, "taro")));
    }

    @ParameterizedTest(name = "同値分割: {0} → {1}")
    @CsvSource({
        "invalidCredentials, 401",
        "userNotFound, 401",
        "invalidRefreshToken, 401",
        "postNotFound, 404",
        "postForbidden, 403",
        "commentNotFound, 404",
        "commentForbidden, 403",
        "profileNotFound, 404",
        "selfFollow, 400",
        "emptyPost, 400",
        "duplicateKey, 409",
        "maxUpload, 400",
    })
    void businessExceptionsMapToStatusWithMessageOnly(String name, int status) throws Exception {
        mockMvc.perform(get("/test/throw/" + name).header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().is(status))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value(Matchers.not(Matchers.blankOrNullString())))
            .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("DuplicateFieldException は 409 で、どの項目が重複したかを fieldErrors で返す")
    void duplicateFieldCarriesFieldName() throws Exception {
        mockMvc.perform(get("/test/throw/duplicateField").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.fieldErrors.email").value("このメールアドレスは既に使用されています"));
    }

    @Test
    @DisplayName("InvalidImageException は 400 で、項目名 image に理由を載せる")
    void invalidImageIsReportedUnderImageField() throws Exception {
        mockMvc.perform(get("/test/throw/invalidImage").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.image").value("画像は JPEG か PNG にしてください"));
    }

    @Test
    @DisplayName("想定外の例外は 500 で固定メッセージを返し、例外の内容を漏らさない")
    void unexpectedExceptionHidesDetails() throws Exception {
        mockMvc.perform(get("/test/throw/unexpected").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("サーバーでエラーが発生しました。時間をおいて再度お試しください"))
            .andExpect(content().string(Matchers.not(Matchers.containsString("secret internal detail"))))
            .andExpect(content().string(Matchers.not(Matchers.containsString("IllegalStateException"))));
    }

    @Test
    @DisplayName("Bean Validation 違反は 400 で項目ごとのメッセージを返し、同じ項目の複数違反は最初の 1 つだけ採用する")
    void validationErrorsAreGroupedByField() throws Exception {
        mockMvc.perform(post("/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"tag\":\"abc\"}")
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("入力内容を確認してください"))
            // name は @NotBlank と @Size の両方に違反するが、値は 1 つの文字列（Map ではない）
            .andExpect(jsonPath("$.fieldErrors.name").value(Matchers.either(
                Matchers.is("名前を入力してください")).or(Matchers.is("名前は3文字以上にしてください"))))
            .andExpect(jsonPath("$.fieldErrors.tag").value("タグは2文字以内にしてください"));
    }

    @Test
    @DisplayName("Bean Validation を通る入力は 200（ハンドラが誤って介入しない）")
    void validInputPasses() throws Exception {
        mockMvc.perform(post("/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"abc\",\"tag\":\"ab\"}")
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("クエリパラメータの制約違反は 400 で、メッセージに制約の文言をそのまま載せる")
    void parameterValidationUsesConstraintMessage() throws Exception {
        mockMvc.perform(get("/test/param").param("q", "abcd").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("q は3文字以内にしてください"))
            .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    @DisplayName("同値分割: 壊れた JSON / 非対応の Content-Type / 型不一致のパス変数 / 欠けた multipart パート はすべて 400 の共通形式")
    void malformedRequestsAreBadRequest() throws Exception {
        String expected = "リクエストの形式が正しくありません";

        mockMvc.perform(post("/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json")
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(expected));
        mockMvc.perform(post("/test/validate")
                .contentType(MediaType.TEXT_PLAIN)
                .content("name=abc")
                .header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(expected));
        mockMvc.perform(get("/test/typed/abc").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(expected));
        mockMvc.perform(multipart("/test/part").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(expected));
    }

    @Test
    @DisplayName("存在しないパスは 404 の共通形式（Spring 既定のエラーページにしない）")
    void unknownPathIsNotFoundInCommonFormat() throws Exception {
        mockMvc.perform(get("/test/no-such-path").header("Authorization", "Bearer " + VALID_TOKEN))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("指定されたリソースが見つかりません"));
    }
}
