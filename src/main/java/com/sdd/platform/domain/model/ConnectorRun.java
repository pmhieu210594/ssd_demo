package com.sdd.platform.domain.model;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConnectorRun {

    private UUID id;
    private UUID connectorId;
    private String connectorName;
    private String provider;
    private UUID repositoryId;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private Status status;
    private Integer recordsReceived;
    private Integer recordsInserted;
    private Integer recordsUpdated;
    private Integer recordsSkipped;
    private Integer recordsError;
    private String errorMessage;
    private String traceId;

    public enum Status { RUNNING, SUCCESS, FAILED }

    public Integer getRecordsIngested() {
        int inserted = recordsInserted == null ? 0 : recordsInserted;
        int updated = recordsUpdated == null ? 0 : recordsUpdated;
        return inserted + updated;
    }
}
