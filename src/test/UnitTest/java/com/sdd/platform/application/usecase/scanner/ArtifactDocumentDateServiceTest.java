package com.sdd.platform.application.usecase.scanner;

import com.sdd.platform.application.port.out.integration.ArtifactScannerSourcePort;
import com.sdd.platform.application.usecase.scanner.ArtifactDocumentDateService.DocumentDate;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactDocumentDateServiceTest {

    private static final String REPO_FULL_NAME = "nktrung/Demo-Project";
    private static final String TICKET_DIR_PREFIX = "docs/changes/PHASE-DWELL-TIME/";

    private static class FakeSource implements ArtifactScannerSourcePort {
        private final Map<String, byte[]> blobsBySha = new LinkedHashMap<>();

        void putBlob(String sha, String content) {
            blobsBySha.put(sha, content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public ResolvedRevision resolveRevision(String repositoryFullName, String refOrSha) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, GitHubTreeEntry> listTree(String repositoryFullName, String revisionSha) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] readBlob(String repositoryFullName, String blobSha) {
            return blobsBySha.get(blobSha);
        }
    }

    private ArtifactSnapshot snapshotFor(UUID snapshotId, boolean exists) {
        return new ArtifactSnapshot(
                snapshotId, null, UUID.randomUUID(), UUID.randomUUID(), "PHASE-DWELL-TIME", null, null,
                UUID.randomUUID(), UUID.randomUUID(), "SPEC_PACK", "Spec Pack", "spec-pack.md", true, "1",
                TICKET_DIR_PREFIX + "spec-pack.md", exists, "hash", null, 10L, null, false, false, "OK", null, null);
    }

    @Test
    void extractsFullHeaderWithDateAndTime() {
        FakeSource source = new FakeSource();
        source.putBlob("sha-1", """
                # Spec Pack

                **create_date**: 2026-08-19 09:00:00
                **update_date**: 2026-08-20 10:30:05
                """);
        ArtifactDocumentDateService service = new ArtifactDocumentDateService(source);
        UUID snapshotId = UUID.randomUUID();
        Map<String, ArtifactSnapshot> snapshotsByFileName = Map.of("spec-pack.md", snapshotFor(snapshotId, true));
        Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree = Map.of(
                TICKET_DIR_PREFIX + "spec-pack.md",
                new ArtifactScannerSourcePort.GitHubTreeEntry(TICKET_DIR_PREFIX + "spec-pack.md", "sha-1", "blob", 10L));

        List<DocumentDate> result = service.extract(REPO_FULL_NAME, "ticket-1", snapshotsByFileName, tree, TICKET_DIR_PREFIX);

        assertEquals(1, result.size());
        DocumentDate documentDate = result.get(0);
        assertEquals(snapshotId, documentDate.artifactSnapshotId());
        assertEquals(OffsetDateTime.of(2026, 8, 19, 9, 0, 0, 0, ZoneOffset.UTC), documentDate.createAt());
        assertEquals(OffsetDateTime.of(2026, 8, 20, 10, 30, 5, 0, ZoneOffset.UTC), documentDate.updateAt());
    }

    @Test
    void extractsDateOnlyHeader() {
        FakeSource source = new FakeSource();
        source.putBlob("sha-2", """
                # Spec Pack

                **create_date**: 2026-08-19
                **update_date**: 2026-08-20
                """);
        ArtifactDocumentDateService service = new ArtifactDocumentDateService(source);
        UUID snapshotId = UUID.randomUUID();
        Map<String, ArtifactSnapshot> snapshotsByFileName = Map.of("spec-pack.md", snapshotFor(snapshotId, true));
        Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree = Map.of(
                TICKET_DIR_PREFIX + "spec-pack.md",
                new ArtifactScannerSourcePort.GitHubTreeEntry(TICKET_DIR_PREFIX + "spec-pack.md", "sha-2", "blob", 10L));

        List<DocumentDate> result = service.extract(REPO_FULL_NAME, "ticket-1", snapshotsByFileName, tree, TICKET_DIR_PREFIX);

        assertEquals(1, result.size());
        assertEquals(OffsetDateTime.of(2026, 8, 19, 0, 0, 0, 0, ZoneOffset.UTC), result.get(0).createAt());
        assertEquals(OffsetDateTime.of(2026, 8, 20, 0, 0, 0, 0, ZoneOffset.UTC), result.get(0).updateAt());
    }

    @Test
    void missingUpdateDateFieldYieldsNullWithoutThrowing() {
        FakeSource source = new FakeSource();
        source.putBlob("sha-3", """
                # Spec Pack

                **create_date**: 2026-08-19
                """);
        ArtifactDocumentDateService service = new ArtifactDocumentDateService(source);
        UUID snapshotId = UUID.randomUUID();
        Map<String, ArtifactSnapshot> snapshotsByFileName = Map.of("spec-pack.md", snapshotFor(snapshotId, true));
        Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree = Map.of(
                TICKET_DIR_PREFIX + "spec-pack.md",
                new ArtifactScannerSourcePort.GitHubTreeEntry(TICKET_DIR_PREFIX + "spec-pack.md", "sha-3", "blob", 10L));

        List<DocumentDate> result = service.extract(REPO_FULL_NAME, "ticket-1", snapshotsByFileName, tree, TICKET_DIR_PREFIX);

        assertEquals(1, result.size());
        assertEquals(OffsetDateTime.of(2026, 8, 19, 0, 0, 0, 0, ZoneOffset.UTC), result.get(0).createAt());
        assertNull(result.get(0).updateAt());
    }

    @Test
    void unparsableHeaderValueYieldsNullWithoutThrowing() {
        FakeSource source = new FakeSource();
        source.putBlob("sha-4", """
                # Spec Pack

                **create_date**: not-a-date
                **update_date**: also-not-a-date
                """);
        ArtifactDocumentDateService service = new ArtifactDocumentDateService(source);
        UUID snapshotId = UUID.randomUUID();
        Map<String, ArtifactSnapshot> snapshotsByFileName = Map.of("spec-pack.md", snapshotFor(snapshotId, true));
        Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree = Map.of(
                TICKET_DIR_PREFIX + "spec-pack.md",
                new ArtifactScannerSourcePort.GitHubTreeEntry(TICKET_DIR_PREFIX + "spec-pack.md", "sha-4", "blob", 10L));

        List<DocumentDate> result = service.extract(REPO_FULL_NAME, "ticket-1", snapshotsByFileName, tree, TICKET_DIR_PREFIX);

        assertEquals(1, result.size());
        assertNull(result.get(0).createAt());
        assertNull(result.get(0).updateAt());
    }

    @Test
    void skipsFilesNotExistingOrMissingFromTree() {
        FakeSource source = new FakeSource();
        ArtifactDocumentDateService service = new ArtifactDocumentDateService(source);
        UUID snapshotId = UUID.randomUUID();
        Map<String, ArtifactSnapshot> snapshotsByFileName = Map.of(
                "spec-pack.md", snapshotFor(snapshotId, false),
                "impl-plan.md", snapshotFor(UUID.randomUUID(), true));
        Map<String, ArtifactScannerSourcePort.GitHubTreeEntry> tree = Map.of();

        List<DocumentDate> result = service.extract(REPO_FULL_NAME, "ticket-1", snapshotsByFileName, tree, TICKET_DIR_PREFIX);

        assertTrue(result.isEmpty());
    }
}
