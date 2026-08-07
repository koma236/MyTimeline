package com.example.mytimeline.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
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
}
