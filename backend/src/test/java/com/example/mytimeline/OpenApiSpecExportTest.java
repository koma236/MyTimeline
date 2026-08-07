package com.example.mytimeline;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * OpenAPI 仕様書を {@code docs/api/openapi.json} に書き出す。
 *
 * <p>springdoc が生成する仕様書は起動中のサーバーからしか取得できないため、
 * このテストで静的ファイルとしてリポジトリに書き出し、PR の diff で
 * API の変更をレビューできるようにする。CI（openapi-spec-check.yml）は
 * このテストを実行してファイルが最新かを検査するので、API を変更したら
 * このテストを流して差分をコミットすること。</p>
 *
 * <p>仕様書の生成にビジネスロジックは動かないため、Flyway を無効化して
 * PostgreSQL なしで起動する（DataSource への接続は最初のクエリまで行われない）。
 * springdoc-openapi-gradle-plugin を使わないのも同じ理由で、
 * あちらはアプリの実起動＝DB が必要になり CI で完結しなくなる。</p>
 */
@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class OpenApiSpecExportTest {

    /** 書き出し先ディレクトリ。テストの作業ディレクトリ（backend/）からリポジトリ直下の docs/ を指す相対パス。 */
    private static final Path OUTPUT_DIR = Path.of("..", "docs", "api");

    /** 書き出し先ファイル。 */
    private static final Path OUTPUT_PATH = OUTPUT_DIR.resolve("openapi.json");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("/v3/api-docs の内容を docs/api/openapi.json に書き出す")
    void exportOpenApiSpec() throws Exception {
        // キー順ソートと整形は application.properties の springdoc.writer-* 設定が行うため、
        // レスポンスをそのまま書き出せば決定的な出力になる
        String spec = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Files.createDirectories(OUTPUT_DIR);
        // 改行は OS 非依存の LF に固定する。System.lineSeparator() だと環境によって差分が出る
        Files.writeString(OUTPUT_PATH, spec + "\n", StandardCharsets.UTF_8);
    }
}
