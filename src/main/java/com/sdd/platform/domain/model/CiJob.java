package com.sdd.platform.domain.model;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CiJob {
    private UUID id;
    private UUID ciRunId;
    private String externalJobId;
    private String jobName;
    private String status;
    private Integer durationSeconds;
    private String failureCategory;
    private String failureSummary;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
}
