package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.domain.model.SecurityScan;
import com.sdd.platform.infrastructure.persistence.mapper.SecurityScanMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SecurityScanRepositoryAdapterTest {

    @Test
    void save_inserts_new_scans_even_when_id_is_already_assigned() {
        SecurityScanMapper mapper = Mockito.mock(SecurityScanMapper.class);
        SecurityScanRepositoryAdapter adapter = new SecurityScanRepositoryAdapter(mapper);
        SecurityScan scan = SecurityScan.builder()
                .securityScanId(UUID.randomUUID())
                .repositoryNameMasked("pdkhoa2505/Test_CI")
                .scannerType("SAST")
                .build();

        adapter.save(scan);

        verify(mapper).insert(scan);
        verify(mapper, never()).update(scan);
    }
}
