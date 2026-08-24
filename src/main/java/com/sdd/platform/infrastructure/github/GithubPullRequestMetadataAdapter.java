package com.sdd.platform.infrastructure.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.sdd.platform.application.port.out.integration.GithubPullRequestMetadataPort;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ChangedFileSnapshot;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.CommitSnapshot;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.PullRequestGraph;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.PullRequestSummary;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ReviewCommentSnapshot;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ReviewSnapshot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class GithubPullRequestMetadataAdapter implements GithubPullRequestMetadataPort {

    private final WebClient githubWebClient;

    public GithubPullRequestMetadataAdapter(@Qualifier("githubWebClient") WebClient githubWebClient) {
        this.githubWebClient = githubWebClient;
    }

    @Override
    public PullRequestGraph fetchPullRequest(String repositoryFullName, int pullRequestNumber) {
        RepoParts repo = parseRepo(repositoryFullName);
        JsonNode response = githubWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/pulls/{number}")
                        .build(repo.owner(), repo.name(), pullRequestNumber))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        if (response == null || response.isNull()) {
            throw new IllegalStateException("Empty GitHub pull request response");
        }

        List<CommitSnapshot> commits = new ArrayList<>();
        for (JsonNode item : fetchPagedArray("/repos/{owner}/{repo}/pulls/{number}/commits", repo, pullRequestNumber)) {
            String sha = item.path("sha").asText("");
            String message = item.path("commit").path("message").asText("");
            String authorLogin = item.path("author").path("login").asText("");
            String authorName = item.path("commit").path("author").path("name").asText("");
            OffsetDateTime committedAt = parseDate(item.path("commit").path("committer").path("date").asText(null));
            if (committedAt == null) {
                committedAt = parseDate(item.path("commit").path("author").path("date").asText(null));
            }
            String htmlUrl = item.path("html_url").asText("");
            if (!sha.isBlank()) {
                commits.add(new CommitSnapshot(sha, message, authorLogin, authorName, committedAt, htmlUrl));
            }
        }

        List<ChangedFileSnapshot> changedFiles = new ArrayList<>();
        for (JsonNode item : fetchPagedArray("/repos/{owner}/{repo}/pulls/{number}/files", repo, pullRequestNumber)) {
            String path = item.path("filename").asText("");
            if (path.isBlank()) {
                continue;
            }
            changedFiles.add(new ChangedFileSnapshot(
                    path,
                    item.path("status").asText("modified"),
                    item.path("additions").asInt(0),
                    item.path("deletions").asInt(0),
                    item.path("sha").asText("")
            ));
        }

        List<JsonNode> reviewNodes = fetchPagedArray("/repos/{owner}/{repo}/pulls/{number}/reviews", repo, pullRequestNumber);
        String reviewState = resolveReviewState(reviewNodes);
        List<ReviewSnapshot> reviews = new ArrayList<>();
        for (JsonNode item : reviewNodes) {
            String externalReviewId = item.path("id").asText("");
            if (externalReviewId.isBlank()) {
                continue;
            }
            reviews.add(new ReviewSnapshot(
                    externalReviewId,
                    item.path("user").path("login").asText(""),
                    item.path("state").asText("").trim().toUpperCase(Locale.ROOT),
                    item.path("body").asText(""),
                    item.path("path").asText(""),
                    parseDate(item.path("submitted_at").asText(null)),
                    item.path("user").path("login").asText("")
            ));
        }

        List<ReviewCommentSnapshot> reviewComments = new ArrayList<>();
        for (JsonNode item : fetchPagedArray("/repos/{owner}/{repo}/pulls/{number}/comments", repo, pullRequestNumber)) {
            String externalCommentId = item.path("id").asText("");
            if (externalCommentId.isBlank()) {
                continue;
            }
            String externalReviewId = item.path("pull_request_review_id").asText(null);
            reviewComments.add(new ReviewCommentSnapshot(
                    externalCommentId,
                    externalReviewId,
                    item.path("user").path("login").asText(""),
                    item.path("body").asText(""),
                    item.path("path").asText(""),
                    item.path("line").isNull() ? item.path("original_line").asInt(0) : item.path("line").asInt(0),
                    parseDate(item.path("created_at").asText(null))
            ));
        }

        Set<String> labels = new LinkedHashSet<>();
        JsonNode labelsNode = response.path("labels");
        if (labelsNode.isArray()) {
            for (JsonNode label : labelsNode) {
                String name = label.path("name").asText("");
                if (!name.isBlank()) {
                    labels.add(name);
                }
            }
        }

        return new PullRequestGraph(
                response.path("number").asInt(pullRequestNumber),
                response.path("title").asText(""),
                response.path("body").asText(null),
                response.path("html_url").asText(""),
                response.path("state").asText(""),
                parseDate(response.path("merged_at").asText(null)) != null,
                response.path("head").path("ref").asText(""),
                response.path("head").path("sha").asText(""),
                response.path("base").path("ref").asText(""),
                parseDate(response.path("created_at").asText(null)),
                parseDate(response.path("updated_at").asText(null)),
                parseDate(response.path("merged_at").asText(null)),
                parseDate(response.path("closed_at").asText(null)),
                response.path("user").path("login").asText(""),
                List.copyOf(labels),
                reviewState,
                List.copyOf(commits),
                List.copyOf(changedFiles),
                List.copyOf(reviews),
                List.copyOf(reviewComments)
        );
    }

    @Override
    public List<PullRequestSummary> listPullRequests(String repositoryFullName, boolean includeClosed) {
        RepoParts repo = parseRepo(repositoryFullName);
        String state = includeClosed ? "all" : "open";
        List<PullRequestSummary> summaries = new ArrayList<>();
        int page = 1;
        while (true) {
            final int pageNumber = page;
            JsonNode response = githubWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/pulls")
                            .queryParam("state", state)
                            .queryParam("per_page", 100)
                            .queryParam("page", pageNumber)
                            .queryParam("sort", "updated")
                            .queryParam("direction", "desc")
                            .build(repo.owner(), repo.name()))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (response == null || !response.isArray() || response.isEmpty()) {
                break;
            }
            for (JsonNode item : response) {
                int number = item.path("number").asInt(0);
                if (number <= 0) {
                    continue;
                }
                OffsetDateTime updatedAt = parseDate(item.path("updated_at").asText(null));
                OffsetDateTime mergedAt = parseDate(item.path("merged_at").asText(null));
                OffsetDateTime closedAt = parseDate(item.path("closed_at").asText(null));
                summaries.add(new PullRequestSummary(
                        number,
                        item.path("state").asText(""),
                        mergedAt != null,
                        updatedAt,
                        mergedAt,
                        closedAt
                ));
            }
            if (response.size() < 100) {
                break;
            }
            page++;
        }
        summaries.sort(Comparator.comparing(PullRequestSummary::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparingInt(PullRequestSummary::number));
        return summaries;
    }

    private List<JsonNode> fetchPagedArray(String pathTemplate, RepoParts repo, int pullRequestNumber) {
        List<JsonNode> items = new ArrayList<>();
        int page = 1;
        while (true) {
            final int pageNumber = page;
            JsonNode response = githubWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(pathTemplate)
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
                items.add(item);
            }
            if (response.size() < 100) {
                break;
            }
            page++;
        }
        return items;
    }

    private String resolveReviewState(List<JsonNode> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return "UNKNOWN";
        }
        for (JsonNode review : reviews) {
            String normalized = review.path("state").asText("").trim().toUpperCase(Locale.ROOT);
            if ("CHANGES_REQUESTED".equals(normalized)) {
                return normalized;
            }
        }
        for (JsonNode review : reviews) {
            String normalized = review.path("state").asText("").trim().toUpperCase(Locale.ROOT);
            if ("APPROVED".equals(normalized)) {
                return normalized;
            }
        }
        return "REVIEW_REQUIRED";
    }

    private RepoParts parseRepo(String repositoryFullName) {
        if (repositoryFullName == null || repositoryFullName.isBlank()) {
            throw new IllegalArgumentException("repository_full_name is required");
        }
        String[] parts = repositoryFullName.split("/", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("INVALID_REPOSITORY_FULL_NAME");
        }
        return new RepoParts(parts[0], parts[1]);
    }

    private OffsetDateTime parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private record RepoParts(String owner, String name) {
    }
}
