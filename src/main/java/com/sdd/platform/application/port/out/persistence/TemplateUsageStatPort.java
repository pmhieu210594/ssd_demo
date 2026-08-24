package com.sdd.platform.application.port.out.persistence;

import java.util.UUID;

public interface TemplateUsageStatPort {

    void recordCheck(UUID projectId, UUID repositoryId, UUID phaseId, boolean matched);
}
