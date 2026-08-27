package com.sdd.platform.infrastructure.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.sdd.platform.application.port.out.integration.ArtifactScannerSourcePort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GithubArtifactScannerSourceAdapter implements ArtifactScannerSourcePort {

    private final WebClient githubWebClient;

    public GithubArtifactScannerSourceAdapter(@Qualifier("githubWebClient") WebClient githubWebClient) {
        this.githubWebClient = githubWebClient;
    }

    @Override
    public ResolvedRevision resolveRevision(String repositoryFullName, String refOrSha) {
        RepoParts repo = parseRepo(repositoryFullName);
        String revision = normalizeRevision(refOrSha);
        if (isSha(revision)) {
            return new ResolvedRevision(revision, null);
        }

        JsonNode ref = githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/git/ref/heads/{branch}")
                        .build(repo.owner(), repo.name(), revision))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String sha = ref == null ? "" : ref.path("object").path("sha").asText("");
        if (sha.isBlank()) {
            throw new IllegalStateException("Unable to resolve GitHub revision for " + repositoryFullName);
        }
        return new ResolvedRevision(sha, null);
    }

    @Override
    public Map<String, GitHubTreeEntry> listTree(String repositoryFullName, String revisionSha) {
        RepoParts repo = parseRepo(repositoryFullName);
        JsonNode response = githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/git/trees/{sha}")
                        .queryParam("recursive", 1)
                        .build(repo.owner(), repo.name(), revisionSha))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        Map<String, GitHubTreeEntry> entries = new LinkedHashMap<>();
        if (response == null) {
            return entries;
        }
        JsonNode tree = response.path("tree");
        if (!tree.isArray()) {
            return entries;
        }
        for (JsonNode item : tree) {
            String path = item.path("path").asText("");
            String sha = item.path("sha").asText("");
            String type = item.path("type").asText("");
            Long size = item.hasNonNull("size") ? item.path("size").asLong() : null;
            if (!path.isBlank()) {
                entries.put(path, new GitHubTreeEntry(path, sha, type, size));
            }
        }
        return entries;
    }

    @Override
    public byte[] readBlob(String repositoryFullName, String blobSha) {
        RepoParts repo = parseRepo(repositoryFullName);
        JsonNode response = githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/git/blobs/{sha}")
                        .build(repo.owner(), repo.name(), blobSha))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        if (response == null) {
            throw new IllegalStateException("Empty GitHub blob response for " + blobSha);
        }
        String encoding = response.path("encoding").asText("");
        String content = response.path("content").asText("");
        if (!"base64".equalsIgnoreCase(encoding)) {
            throw new IllegalStateException("Unsupported GitHub blob encoding: " + encoding);
        }
        String normalized = content.replace("\n", "").replace("\r", "");
        return Base64.getDecoder().decode(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isSha(String value) {
        return value != null && value.matches("(?i)^[0-9a-f]{40}$");
    }

    private static String normalizeRevision(String refOrSha) {
        if (refOrSha == null) {
            return "";
        }
        String trimmed = refOrSha.trim();
        if (trimmed.startsWith("refs/heads/")) {
            return trimmed.substring("refs/heads/".length());
        }
        return trimmed;
    }

    private static RepoParts parseRepo(String repositoryFullName) {
        if (repositoryFullName == null || repositoryFullName.isBlank()) {
            throw new IllegalArgumentException("repository_full_name is required");
        }
        String[] parts = repositoryFullName.split("/", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("INVALID_REPOSITORY_FULL_NAME");
        }
        return new RepoParts(parts[0], parts[1]);
    }

    private record RepoParts(String owner, String name) {}
}
