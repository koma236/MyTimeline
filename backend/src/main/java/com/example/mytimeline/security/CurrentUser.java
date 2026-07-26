package com.example.mytimeline.security;

/**
 * 認証済みユーザーを表す値。
 *
 * <p>Spring Security の principal として SecurityContext に格納され、
 * Controller では {@code @AuthenticationPrincipal CurrentUser} で受け取れる。
 * JWT から復元できる情報だけを持ち、DB は参照しない。</p>
 */
public record CurrentUser(Long id, String username) {
}
