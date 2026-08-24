package com.sdd.platform.application.port.out.integration;

import java.time.OffsetDateTime;
import java.util.Map;

public interface ArtifactScannerSourcePort {

    ResolvedRevision resolveRevision(String repositoryFullName, String refOrSha);

    Map<String, GitHubTreeEntry> listTree(String repositoryFullName, String revisionSha);

    byte[] readBlob(String repositoryFullName, String blobSha);

    record ResolvedRevision(String revisionSha, OffsetDateTime committedAt) {}

    record GitHubTreeEntry(String path, String sha, String type, Long size) {}
}
