/**
 * Domain layer — pure business model.
 *
 * <h2>Allowed dependencies</h2>
 * <ul>
 *   <li>JDK.</li>
 *   <li>Lombok annotations (compile-time).</li>
 *   <li>Problem-domain libraries that are not application frameworks
 *       (e.g., {@code commonmark} for markdown parsing, {@code snakeyaml}
 *       for YAML — they describe the data the domain works with).</li>
 * </ul>
 *
 * <h2>Forbidden dependencies</h2>
 * <ul>
 *   <li>Spring (no {@code @Component}, {@code @Service}, etc.).</li>
 *   <li>MyBatis / Jackson / any persistence or serialization framework.</li>
 *   <li>{@code com.sdd.platform.application}, {@code com.sdd.platform.infrastructure}, {@code com.sdd.platform.web}.</li>
 * </ul>
 *
 * Enforced by {@code LayerEnforcementTest} (ArchUnit).
 */
package com.sdd.platform.domain;
