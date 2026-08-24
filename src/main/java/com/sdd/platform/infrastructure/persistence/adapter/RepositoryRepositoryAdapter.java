package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.RepositoryRepositoryPort;
import com.sdd.platform.domain.model.RepositoryModel;
import com.sdd.platform.infrastructure.persistence.mapper.RepositoryMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RepositoryRepositoryAdapter implements RepositoryRepositoryPort {

    private final RepositoryMapper mapper;

    public RepositoryRepositoryAdapter(RepositoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<RepositoryModel> findById(UUID repositoryId) {
        return mapper.findById(repositoryId);
    }

    @Override
    public List<RepositoryModel> findPage(UUID projectId, String status, String keyword, int offset, int limit) {
        return mapper.findPage(projectId, status, keyword, offset, limit);
    }

    @Override
    public long count(UUID projectId, String status, String keyword) {
        return mapper.count(projectId, status, keyword);
    }

    @Override
    public boolean existsActiveName(UUID projectId, String repoNameMasked, UUID excludeRepositoryId) {
        return mapper.existsActiveName(projectId, repoNameMasked, excludeRepositoryId);
    }

    @Override
    public boolean existsActiveProject(UUID projectId) {
        return mapper.existsActiveProject(projectId);
    }

    @Override
    public RepositoryModel insert(RepositoryModel repository) {
        mapper.insert(repository);
        return repository;
    }

    @Override
    public int update(RepositoryModel repository) {
        return mapper.update(repository);
    }

    @Override
    public int softDelete(UUID repositoryId, String deletedBy, OffsetDateTime deletedAt, String updatedBy, OffsetDateTime updatedAt) {
        return mapper.softDelete(repositoryId, deletedBy, deletedAt, updatedBy, updatedAt);
    }
}
