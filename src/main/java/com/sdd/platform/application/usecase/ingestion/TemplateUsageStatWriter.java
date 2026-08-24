package com.sdd.platform.application.usecase.ingestion;

import com.sdd.platform.application.port.out.persistence.TemplateUsageStatPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Isolated upsert boundary so a template-usage counter write never blocks or
 * rolls back the primary webhook/PR-ingestion flow. Uses REQUIRES_NEW to spawn
 * an independent transaction per call, committing/failing on its own. A separate
 * Spring bean is required here (mirrors {@code AdminAuditLogWriter}) so the
 * {@code @Transactional} annotation applies — calling this from within
 * {@link GithubWebhookService} itself would bypass the proxy (self-invocation).
 */
@Component
public class TemplateUsageStatWriter {

    private final TemplateUsageStatPort port;

    public TemplateUsageStatWriter(TemplateUsageStatPort port) {
        this.port = port;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(UUID projectId, UUID repositoryId, UUID phaseId, boolean matched) {
        port.recordCheck(projectId, repositoryId, phaseId, matched);
    }
}
