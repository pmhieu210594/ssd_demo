package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.domain.model.EvidenceRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvidenceRepositoryPort {

    List<EvidenceRepository> findByProjectId(UUID projectId);

    Optional<EvidenceRepository> findByRepositoryId(UUID repositoryId);

    Optional<EvidenceRepository> findByRepositoryNameMaskedAndHostType(String repositoryNameMasked,
                                                                       String hostType);
}
