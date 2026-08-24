package com.sdd.platform.domain.model;

import lombok.*;

import java.time.OffsetDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Artifact {

    private Long id;
    private Long ticketId;
    private ArtifactType artifactType;
    private String filePath;
    private String contentHash;
    private Integer schemaVersion;
    private boolean templateOnly;

    /** JSON array stored as text, e.g. '["scope","ac"]'. */
    private String requiredFieldsMissing;

    private OffsetDateTime lastCollectedAt;
    private OffsetDateTime updatedAt;

    /** Mirrors `artifact_type` text in DB. Names match canonical SDD artifact filenames. */
    public enum ArtifactType {
        SPEC_PACK,
        SOURCES,
        IMPL_PLAN,
        REVIEW_CHECKLIST,
        SELF_REVIEW,
        TEST_PLAN,
        TEST_RESULTS,
        BLACKBOX_TESTCASES,
        TEST_DATA,
        REPORT
    }
}
