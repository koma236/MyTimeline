package com.example.mytimeline.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * リクエストごとに {@code Authorization: Bearer <token>} を検証し、
 * 有効なら「ログイン済み」として SecurityContext に記録するフィルタ。
 *
 * <p>トークンが無い・壊れている・期限切れの場合でもここでは拒否せず、
 * SecurityContext を空のまま次に進める。実際に 401 を返すかどうかは
 * {@link com.example.mytimeline.config.SecurityConfig} の認可ルールが決める。
 * こうすることで「どの URL が保護されているか」の定義が 1 箇所に集まる。</p>
 *
 * <p>あえて Bean にしていない。{@code Filter} 型の Bean は Spring Security の
 * フィルタチェーンより手前にサーブレットフィルタとして自動登録されてしまい、
 * そこで認証情報をセットしても後続の {@code SecurityContextHolderFilter} に消され、
 * さらに {@link OncePerRequestFilter} の重複実行防止でチェーン内では実行されなくなる。
 * {@code SecurityConfig} から直接生成してチェーンに組み込むこと。</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        extractToken(request)
            .flatMap(jwtService::parseToken)
            .ifPresent(currentUser -> authenticate(currentUser, request));

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private void authenticate(CurrentUser currentUser, HttpServletRequest request) {
        // ロールは扱わないため権限リストは空にする（認可は「ログイン済みか」のみで判断する）
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(currentUser, null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
