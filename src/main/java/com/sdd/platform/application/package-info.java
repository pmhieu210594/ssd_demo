/**
 * Application layer — use case orchestration.
 *
 * <h2>Structure</h2>
 * <ul>
 *   <li>{@code usecase/<bounded-context>/}: facade services that orchestrate domain + ports.</li>
 *   <li>{@code port/out/persistence/}: output ports backed by the database.</li>
 *   <li>{@code port/out/integration/}: output ports for external systems (connectors, ingestion).</li>
 *   <li>{@code exception/}: application-level exceptions (e.g., access denied, conflict).</li>
 * </ul>
 *
 * <h2>Allowed dependencies</h2>
 * <ul>
 *   <li>{@code com.sdd.platform.domain.*}</li>
 *   <li>Spring stereotypes ({@code @Service}, {@code @Transactional}) — application classes are wired by Spring.</li>
 * </ul>
 *
 * <h2>Forbidden dependencies</h2>
 * <ul>
 *   <li>{@code com.sdd.platform.infrastructure.*}</li>
 *   <li>{@code com.sdd.platform.web.*}</li>
 * </ul>
 *
 * Enforced by {@code LayerEnforcementTest} (ArchUnit).
 */
package com.sdd.platform.application;
