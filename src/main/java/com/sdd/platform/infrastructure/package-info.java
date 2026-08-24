/**
 * Infrastructure layer — adapters to external systems.
 *
 * <h2>Structure</h2>
 * <ul>
 *   <li>{@code <external-system>/}: one folder per source (github, jira, circleci, gitlocal). Holds connectors, OAuth clients, webhook verifiers.</li>
 *   <li>{@code persistence/mapper/}: MyBatis Mapper interfaces.</li>
 *   <li>{@code persistence/adapter/}: implementations of {@code application.port.out.persistence.*Port}.</li>
 * </ul>
 *
 * <h2>Allowed dependencies</h2>
 * <ul>
 *   <li>{@code com.sdd.platform.domain.*}</li>
 *   <li>{@code com.sdd.platform.application.port.*} — adapters implement output ports.</li>
 *   <li>Spring, MyBatis, Jackson, WebClient — full external framework access.</li>
 * </ul>
 *
 * <h2>Forbidden dependencies</h2>
 * <ul>
 *   <li>{@code com.sdd.platform.application.usecase.*} — adapters must not call application services.</li>
 *   <li>{@code com.sdd.platform.web.*}</li>
 * </ul>
 *
 * Enforced by {@code LayerEnforcementTest} (ArchUnit).
 */
package com.sdd.platform.infrastructure;
