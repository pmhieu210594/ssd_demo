package com.sdd.platform.web.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inject the locally-stored {@code AppUser} that corresponds to the current
 * OAuth2 principal. Resolved by {@link CurrentAppUserResolver}.
 *
 * Why not Spring Security's {@code @AuthenticationPrincipal}? — that annotation
 * resolves to whatever {@code Authentication#getPrincipal()} returns (an
 * {@code OAuth2User} here). Our domain code wants the {@code AppUser} row, so
 * we use a distinct annotation to avoid colliding with Spring's resolver.
 *
 * Usage:
 * <pre>{@code
 *   @PostMapping("/foo")
 *   public X foo(@CurrentUser AppUser caller) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
