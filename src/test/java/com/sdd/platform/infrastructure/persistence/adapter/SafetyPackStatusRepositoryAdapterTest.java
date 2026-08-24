package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.domain.model.SafetyPackStatus;
import com.sdd.platform.infrastructure.persistence.mapper.SafetyPackStatusMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SafetyPackStatusRepositoryAdapterTest {

    @Test
    void save_inserts_new_statuses_even_when_id_is_already_assigned() {
        SafetyPackStatusMapper mapper = Mockito.mock(SafetyPackStatusMapper.class);
        SafetyPackStatusRepositoryAdapter adapter = new SafetyPackStatusRepositoryAdapter(mapper);
        SafetyPackStatus status = SafetyPackStatus.builder()
                .safetyPackStatusId(UUID.randomUUID())
                .repositoryNameMasked("pdkhoa2505/Test_CI")
                .scanStatus("READY")
                .settingsParseStatus("OK")
                .claudeMdExists(Boolean.TRUE)
                .settingsJsonExists(Boolean.TRUE)
                .rulesExists(Boolean.TRUE)
                .build();

        adapter.save(status);

        verify(mapper).insert(status);
        verify(mapper, never()).update(status);
    }
}
