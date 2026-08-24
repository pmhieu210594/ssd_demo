/**
 * Web layer — driving (primary) adapters.
 *
 * <h2>Structure</h2>
 * <ul>
 *   <li>{@code webhook/}: webhook endpoints (HMAC-authenticated).</li>
 *   <li>{@code rest/}: REST endpoints (session-cookie-authenticated).</li>
 *   <li>{@code dto/}: response DTOs (Dtos.java).</li>
 *   <li>{@code security/}: argument resolvers + future security filters.</li>
 *   <li>{@code exception/}: {@code GlobalExceptionHandler} + {@code ErrorResponse}.</li>
 * </ul>
 *
 * <h2>Allowed dependencies</h2>
 * <ul>
 *   <li>{@code com.sdd.platform.application.*} — controllers call use case facades.</li>
 *   <li>{@code com.sdd.platform.domain.*} — DTO mapping references domain models / exceptions.</li>
 *   <li>Spring Web, Spring Security, Jackson — full HTTP-stack access.</li>
 * </ul>
 *
 * <h2>Forbidden dependencies</h2>
 * <ul>
 *   <li>{@code com.sdd.platform.infrastructure.*} — controllers must not bypass the application layer.</li>
 * </ul>
 *
 * Enforced by {@code LayerEnforcementTest} (ArchUnit).
 */
package com.sdd.platform.web;
