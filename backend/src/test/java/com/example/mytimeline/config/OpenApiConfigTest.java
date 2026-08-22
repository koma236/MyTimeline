package com.example.mytimeline.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * DTO の {@code @Nullable} を OpenAPI の型と required に反映するカスタマイザを検証する。
 *
 * <p>設計技法: 同値分割（スキーマの出どころ）。
 * 「schemas 自体が無い」「DTO でないスキーマ」「properties の無いスキーマ」
 * 「DTO にあるがスキーマに無いフィールド」「通常の DTO」の 5 クラスで、
 * 対象外は触らず、対象だけが書き換わることを見る。</p>
 */
class OpenApiConfigTest {

    private final OpenApiCustomizer customizer = new OpenApiConfig().dtoNullabilityCustomizer();

    private static OpenAPI openApiWith(Map<String, Schema> schemas) {
        return new OpenAPI().components(new Components().schemas(schemas));
    }

    @Test
    @DisplayName("同値分割: components に schemas が無ければ何もしない（例外にならない）")
    void ignoresMissingSchemas() {
        OpenAPI openApi = new OpenAPI().components(new Components());

        customizer.customise(openApi);

        assertThat(openApi.getComponents().getSchemas()).isNull();
    }

    @Test
    @DisplayName("同値分割: DTO パッケージに無い名前のスキーマ（multipart のインライン等）は触らない")
    void ignoresNonDtoSchema() {
        Schema<Object> inline = new Schema<>().properties(Map.of("file", new StringSchema()));
        OpenAPI openApi = openApiWith(new LinkedHashMap<>(Map.of("InlineUploadBody", inline)));

        customizer.customise(openApi);

        assertThat(inline.getRequired()).isNull();
    }

    @Test
    @DisplayName("同値分割: DTO でも properties が無いスキーマは触らない")
    void ignoresSchemaWithoutProperties() {
        Schema<Object> empty = new Schema<>();
        OpenAPI openApi = openApiWith(new LinkedHashMap<>(Map.of("UserResponse", empty)));

        customizer.customise(openApi);

        assertThat(empty.getRequired()).isNull();
    }

    @Test
    @DisplayName("@Nullable のフィールドは型に null を足し、全フィールドを辞書順で required にする")
    void marksNullableTypesAndRequiresAllComponents() {
        // UserResponse: id / username / displayName / email / @Nullable bio / @Nullable avatarUrl / createdAt
        StringSchema bio = new StringSchema();
        StringSchema username = new StringSchema();
        IntegerSchema id = new IntegerSchema();
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put("username", username);
        properties.put("id", id);
        properties.put("bio", bio);
        Schema<Object> userResponse = new Schema<>().properties(properties);
        OpenAPI openApi = openApiWith(new LinkedHashMap<>(Map.of("UserResponse", userResponse)));

        customizer.customise(openApi);

        assertThat(bio.getTypes()).contains("null");
        assertThat(username.getTypes()).doesNotContain("null");
        assertThat(id.getTypes()).doesNotContain("null");
        // スキーマに存在するフィールドだけが required に入り、順序は宣言順ではなく辞書順
        assertThat(userResponse.getRequired()).containsExactly("bio", "id", "username");
    }

    @Test
    @DisplayName("Info と Bearer JWT のセキュリティスキームを持つ OpenAPI を組み立てる")
    void buildsOpenApiWithBearerScheme() {
        OpenAPI openApi = new OpenApiConfig().mytimelineOpenApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("MyTimeline API");
        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("bearer-jwt");
        assertThat(openApi.getComponents().getSecuritySchemes().get("bearer-jwt").getBearerFormat()).isEqualTo("JWT");
        assertThat(openApi.getSecurity()).hasSize(1);
    }
}
