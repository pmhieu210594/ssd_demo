package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.SecurityScanRepositoryPort;
import com.sdd.platform.domain.model.SecurityScan;
import com.sdd.platform.infrastructure.persistence.mapper.SecurityScanMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SecurityScanRepositoryAdapter implements SecurityScanRepositoryPort {

    private final SecurityScanMapper mapper;

    public SecurityScanRepositoryAdapter(SecurityScanMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SecurityScan save(SecurityScan scan) {
        if (scan.getSecurityScanId() == null) {
            scan.setSecurityScanId(java.util.UUID.randomUUID());
        }
        mapper.insert(scan);
        return scan;
    }

    @Override
    public List<SecurityScan> findRecent(int limit) {
        return mapper.findRecent(limit);
    }
}
