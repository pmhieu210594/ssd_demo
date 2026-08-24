package com.sdd.platform.application.usecase.ingestion;

import com.sdd.platform.application.port.out.persistence.AiFindingStatPort;
import com.sdd.platform.application.port.out.persistence.AiFindingStatPort.AiFindingStatRecord;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiFindingStatWriterTest {

    @Test
    void recordStat_delegatesToPortWithSameRecord() {
        AiFindingStatPort port = mock(AiFindingStatPort.class);
        AiFindingStatWriter writer = new AiFindingStatWriter(port);
        AiFindingStatRecord record = new AiFindingStatRecord(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, 1, 2, 2, 2, 0, 2);

        writer.recordStat(record);

        verify(port).upsert(record);
    }
}
