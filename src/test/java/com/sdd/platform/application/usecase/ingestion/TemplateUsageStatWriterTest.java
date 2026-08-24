package com.sdd.platform.application.usecase.ingestion;

import com.sdd.platform.application.port.out.persistence.TemplateUsageStatPort;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TemplateUsageStatWriterTest {

    @Test
    void recordIndependently_delegatesToPortWithSameArguments() {
        TemplateUsageStatPort port = mock(TemplateUsageStatPort.class);
        TemplateUsageStatWriter writer = new TemplateUsageStatWriter(port);
        UUID projectId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        UUID phaseId = UUID.randomUUID();

        writer.recordIndependently(projectId, repositoryId, phaseId, true);

        verify(port).recordCheck(projectId, repositoryId, phaseId, true);
    }
}
