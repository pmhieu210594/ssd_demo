package com.sdd.platform.infrastructure.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.sdd.platform.application.port.out.integration.GithubPullRequestFilesPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class GithubPullRequestFilesAdapter implements GithubPullRequestFilesPort {

    private final WebClient githubWebClient;

    public GithubPullRequestFilesAdapter(@Qualifier("githubWebClient") WebClient githubWebClient) {
        this.githubWebClient = githubWebClient;
    }

    @Override
    public List<String> listChangedFilePaths(String repositoryFullName, int pullRequestNumber) {
        RepoParts repo = parseRepo(repositoryFullName);
        List<String> paths = new ArrayList<>();
        int page = 1;

        while (true) {
            final int pageNumber = page;
            JsonNode response = githubWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/pulls/{number}/files")
                            .queryParam("per_page", 100)
                            .queryParam("page", pageNumber)
                            .build(repo.owner(), repo.name(), pullRequestNumber))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.isArray() || response.isEmpty()) {
                break;
            }

            for (JsonNode item : response) {
                String filename = item.path("filename").asText("");
                if (!filename.isBlank()) {
                    paths.add(filename);
                }
            }

            if (response.size() < 100) {
                break;
            }
            page++;
        }

        return paths;
    }

    @Override
    public Optional<OffsetDateTime> resolveLastCommitAt(String repositoryFullName, int pullRequestNumber) {
        RepoParts repo = parseRepo(repositoryFullName);
        OffsetDateTime lastCommitAt = null;
        int page = 1;

        while (true) {
            final int pageNumber = page;
            JsonNode response = githubWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/pulls/{number}/commits")
                            .queryParam("per_page", 100)
                            .queryParam("page", pageNumber)
                            .build(repo.owner(), repo.name(), pullRequestNumber))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.isArray() || response.isEmpty()) {
                break;
            }

            for (JsonNode item : response) {
                OffsetDateTime candidate = parseCommitTime(item.path("commit").path("committer").path("date").asText(null));
                if (candidate == null) {
                    candidate = parseCommitTime(item.path("commit").path("author").path("date").asText(null));
                }
                if (candidate != null) {
                    lastCommitAt = candidate;
                }
            }

            if (response.size() < 100) {
                break;
            }
            page++;
        }

        return Optional.ofNullable(lastCommitAt);
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

    private static OffsetDateTime parseCommitTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private record RepoParts(String owner, String name) {}
}
