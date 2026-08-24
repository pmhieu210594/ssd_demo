package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseDataQuality;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseEvidenceEvent;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseField;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseMode;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocParsePersistencePort {

    ParseSnapshot upsertSnapshot(ParseSnapshot snapshot);

    void replaceSections(UUID snapshotId, UUID ticketId, List<ParseField> sections);

    Optional<ParseSnapshot> findLatestSnapshot(UUID ticketId, String artifactTypeCode, ParseMode parseMode);

    Optional<ParseSnapshot> findSnapshotById(UUID snapshotId);

    List<ParseField> findSections(UUID snapshotId);

    List<ParseSnapshot> findSnapshots(UUID ticketId, String artifactTypeCode, ParseMode parseMode, int limit);

    void persistEvidenceEvent(ParseEvidenceEvent event);

    void persistDataQuality(ParseDataQuality quality);
}
