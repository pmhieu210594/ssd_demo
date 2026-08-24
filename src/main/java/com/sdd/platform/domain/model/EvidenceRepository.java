package com.sdd.platform.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvidenceRepository {

    private UUID repositoryId;
    private UUID projectId;
    private String repositoryNameMasked;
    private String hostType;
    private String defaultBranch;
    private String repoUrlHash;
}
