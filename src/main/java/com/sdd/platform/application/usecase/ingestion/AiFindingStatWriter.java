package com.sdd.platform.application.usecase.ingestion;

import com.sdd.platform.application.port.out.persistence.AiFindingStatPort;
import com.sdd.platform.application.port.out.persistence.AiFindingStatPort.AiFindingStatRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated upsert boundary so an §8 KPI snapshot write never blocks or rolls
 * back the primary webhook/PR-merge flow. Mirrors {@code TemplateUsageStatWriter}:
 * a separate Spring bean is required for {@code @Transactional(REQUIRES_NEW)}
 * to apply (calling this from within {@link GithubWebhookService} itself would
 * bypass the proxy via self-invocation).
 */
@Component
public class AiFindingStatWriter {

    private final AiFindingStatPort port;

    public AiFindingStatWriter(AiFindingStatPort port) {
        this.port = port;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStat(AiFindingStatRecord record) {
        port.upsert(record);
    }
}
