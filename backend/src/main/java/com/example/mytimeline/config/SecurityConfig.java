package com.example.mytimeline.config;

import com.example.mytimeline.dto.ErrorResponse;
import com.example.mytimeline.security.JwtAuthenticationFilter;
import com.example.mytimeline.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/**
 * 認証・認可の設定。
 *
 * <p>「どの URL が公開で、どこから認証必須か」の定義はこのクラスに集約する。
 * {@link JwtAuthenticationFilter} は判定のみを行い、拒否はここの認可ルールが担当する。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // JWT をヘッダで受け取るステートレス構成のため CSRF トークンは不要
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // refresh / logout はアクセストークンではなく Cookie のリフレッシュトークンで
                // 認可するため permitAll。認証必須にすると「アクセストークンが切れているので
                // 取り直すことも抜けることもできない」状態になる
                .requestMatchers("/api/auth/signup", "/api/auth/login").permitAll()
                .requestMatchers("/api/auth/refresh", "/api/auth/logout").permitAll()
                .requestMatchers("/actuator/health", "/error").permitAll()
                // 上記以外はすべてログイン必須。機能追加時に個別の permitAll を足さない限り保護される
                .anyRequest().authenticated()
            )
            .exceptionHandling(handling -> handling.authenticationEntryPoint(jsonAuthenticationEntryPoint()))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 未認証で保護 API を呼ばれた場合に、HTML ではなく JSON で 401 を返す。
     */
    private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(
                response.getWriter(),
                ErrorResponse.of("ログインが必要です")
            );
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
            "http://localhost:*",
            "https://*.cloudfront.net"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // フロントが Authorization ヘッダでトークンを送れるようにする
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        // リフレッシュトークンを httpOnly Cookie で往復させるため必須。
        // なお setAllowedOrigins("*") は credentials と併用できないが、
        // setAllowedOriginPatterns はこの用途のために用意されているのでそのまま使える
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
