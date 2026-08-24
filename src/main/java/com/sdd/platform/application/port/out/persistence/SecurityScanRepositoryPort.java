package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.domain.model.SecurityScan;

import java.util.List;

public interface SecurityScanRepositoryPort {

    SecurityScan save(SecurityScan scan);

    List<SecurityScan> findRecent(int limit);
}
