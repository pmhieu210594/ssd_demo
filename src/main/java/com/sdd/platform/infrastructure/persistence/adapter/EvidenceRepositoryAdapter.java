package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.EvidenceRepositoryPort;
import com.sdd.platform.domain.model.EvidenceRepository;
import com.sdd.platform.infrastructure.persistence.mapper.EvidenceRepositoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EvidenceRepositoryAdapter implements EvidenceRepositoryPort {

    private final EvidenceRepositoryMapper mapper;

    public EvidenceRepositoryAdapter(EvidenceRepositoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<EvidenceRepository> findByProjectId(UUID projectId) {
        return mapper.findByProjectId(projectId);
    }

    @Override
    public Optional<EvidenceRepository> findByRepositoryId(UUID repositoryId) {
        return mapper.findByRepositoryId(repositoryId);
    }

    @Override
    public Optional<EvidenceRepository> findByRepositoryNameMaskedAndHostType(String repositoryNameMasked,
                                                                              String hostType) {
        return mapper.findByRepositoryNameMaskedAndHostType(repositoryNameMasked, hostType);
    }
}
