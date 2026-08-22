package com.example.mytimeline.config;

import com.example.mytimeline.logging.RequestLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * ログ関連のサーブレットフィルタ登録。
 *
 * <p>{@link RequestLoggingFilter} はセキュリティチェーンの外側・最優先で動かす必要がある。
 * {@code Filter} 型の Bean をそのまま置くと順序が既定（最後尾）になり、Spring Security の
 * フィルタより後ろに回って 401 / 403 を取りこぼすため、{@link FilterRegistrationBean} で
 * 明示的に {@link Ordered#HIGHEST_PRECEDENCE} を指定する。</p>
 */
@Configuration
public class LoggingConfig {

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
