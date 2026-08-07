package com.example.mytimeline.config;

import com.example.mytimeline.dto.PostResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 仕様書（OpenAPI / Swagger UI）の設定。
 *
 * <p>仕様そのものはコントローラと DTO から springdoc が自動生成する。ここでは
 * コードから導出できない情報（タイトルと JWT Bearer 認証スキーム）だけを宣言する。</p>
 *
 * <p>認証スキームは全エンドポイントの既定として適用する。{@link SecurityConfig} の
 * 「明示的に permitAll しない限り認証必須」という方針と対応させるためで、
 * signup / login など認証不要のエンドポイント側に空の
 * {@code @SecurityRequirements} を付けて個別に打ち消す。</p>
 */
@Configuration
public class OpenApiConfig {

    /** Swagger UI の Authorize ボタンに表示される認証スキーム名。 */
    private static final String BEARER_JWT = "bearer-jwt";

    /** required / nullable の反映対象。この配下の record だけを扱う。 */
    private static final String DTO_PACKAGE = PostResponse.class.getPackageName();

    @Bean
    public OpenAPI mytimelineOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("MyTimeline API")
                .description("X（旧 Twitter）風タイムライン SNS の REST API。"
                    + "認証必須のエンドポイントは Authorize ボタンからアクセストークンを設定して試せる。")
                .version("v1"))
            .components(new Components().addSecuritySchemes(BEARER_JWT,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
    }

    /**
     * DTO の {@code @Nullable} 宣言（JSpecify）を仕様書の required / nullable に反映する。
     *
     * <p>springdoc は素の Java record から null 許容を判定できず、そのままでは
     * 全フィールドが「任意」として出力される。それを元にフロントエンドの型を生成すると
     * 全プロパティが optional になり、手書きの型より弱くなってしまう（#39）。
     * dto パッケージは {@code @NullMarked} なので「注釈なし = 非 null = required、
     * {@code @Nullable} = 型に "null" を追加」と機械的に読み替えられる。</p>
     */
    @Bean
    public OpenApiCustomizer dtoNullabilityCustomizer() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            if (schemas == null) {
                return;
            }
            schemas.forEach(OpenApiConfig::applyRecordNullability);
        };
    }

    private static void applyRecordNullability(String schemaName, Schema<?> schema) {
        Class<?> dtoClass;
        try {
            dtoClass = Class.forName(DTO_PACKAGE + "." + schemaName);
        } catch (ClassNotFoundException e) {
            // DTO 由来でないスキーマ（multipart のインラインスキーマなど）は対象外
            return;
        }
        if (!dtoClass.isRecord() || schema.getProperties() == null) {
            return;
        }
        List<String> required = new ArrayList<>();
        for (RecordComponent component : dtoClass.getRecordComponents()) {
            Schema<?> property = (Schema<?>) schema.getProperties().get(component.getName());
            if (property == null) {
                continue;
            }
            // JSpecify の @Nullable は TYPE_USE 注釈のため、component ではなく型側から読む
            if (component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
                // OpenAPI 3.1 では nullable フラグではなく型の配列（例: ["string", "null"]）で表す
                property.addType("null");
            }
            // @Nullable でも required に含める。Jackson は null のフィールドもキーごと
            // 出力する（@JsonInclude を付けない限り）ので「キーは必ず存在し、値が null に
            // なりうる」が正しい契約。required から外すと生成型が optional になってしまう
            required.add(component.getName());
        }
        // 宣言順ではなく辞書順にして、フィールドの並び替えで仕様書に差分が出ないようにする
        required.sort(Comparator.naturalOrder());
        schema.setRequired(required);
    }
}
