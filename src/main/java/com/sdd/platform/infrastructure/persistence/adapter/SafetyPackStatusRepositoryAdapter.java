package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.SafetyPackStatusRepositoryPort;
import com.sdd.platform.domain.model.SafetyPackStatus;
import com.sdd.platform.infrastructure.persistence.mapper.SafetyPackStatusMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SafetyPackStatusRepositoryAdapter implements SafetyPackStatusRepositoryPort {

    private final SafetyPackStatusMapper mapper;

    public SafetyPackStatusRepositoryAdapter(SafetyPackStatusMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SafetyPackStatus save(SafetyPackStatus status) {
        if (status.getSafetyPackStatusId() == null) {
            status.setSafetyPackStatusId(java.util.UUID.randomUUID());
        }
        mapper.insert(status);
        return status;
    }

    @Override
    public List<SafetyPackStatus> findRecent(int limit) {
        return mapper.findRecent(limit);
    }
}
