package com.sdd.platform.application.usecase.ingestion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GithubWorkflowJobMapper {

    private static final Pattern TICKET_KEY_PATTERN = Pattern.compile("\\b[A-Z][A-Z0-9]+-\\d+\\b");
    private static final String GITHUB_WEB_BASE = "https://github.com/";

    private GithubWorkflowJobMapper() {}

    static String normalizeStatus(String jobStatus, String jobConclusion) {
        String status = normalize(jobStatus);
        String conclusion = normalize(jobConclusion);

        return switch (status) {
            case "queued", "requested", "waiting", "pending" -> "QUEUED";
            case "in_progress" -> "IN_PROGRESS";
            case "completed" -> switch (conclusion) {
                case "success" -> "SUCCESS";
                case "failure", "timed_out", "action_required" -> "FAILURE";
                case "cancelled" -> "CANCELLED";
                case "skipped" -> "SKIPPED";
                default -> "UNKNOWN";
            };
            default -> switch (conclusion) {
                case "success" -> "SUCCESS";
                case "failure", "timed_out", "action_required" -> "FAILURE";
                case "cancelled" -> "CANCELLED";
                case "skipped" -> "SKIPPED";
                case "queued", "requested", "waiting", "pending" -> "QUEUED";
                case "in_progress" -> "IN_PROGRESS";
                default -> "UNKNOWN";
            };
        };
    }

    static String resolveCiUrl(
            String jobUrl,
            String workflowRunUrl,
            String repositoryFullName,
            String workflowRunId,
            String externalJobId
    ) {
        if (jobUrl != null && !jobUrl.isBlank()) {
            return jobUrl.trim();
        }
        if (workflowRunUrl != null && !workflowRunUrl.isBlank()) {
            return workflowRunUrl.trim();
        }
        String derivedJobUrl = buildGitHubJobUrl(repositoryFullName, workflowRunId, externalJobId);
        if (derivedJobUrl != null) {
            return derivedJobUrl;
        }
        String derivedRunUrl = buildGitHubRunUrl(repositoryFullName, workflowRunId);
        if (derivedRunUrl != null) {
            return derivedRunUrl;
        }
        return null;
    }

    static String buildGitHubRunUrl(String repositoryFullName, String workflowRunId) {
        if (repositoryFullName == null || repositoryFullName.isBlank()) {
            return null;
        }
        if (workflowRunId == null || workflowRunId.isBlank()) {
            return null;
        }
        return GITHUB_WEB_BASE + repositoryFullName.trim() + "/actions/runs/" + workflowRunId.trim();
    }

    static String buildGitHubJobUrl(String repositoryFullName, String workflowRunId, String externalJobId) {
        String runUrl = buildGitHubRunUrl(repositoryFullName, workflowRunId);
        if (runUrl == null || externalJobId == null || externalJobId.isBlank()) {
            return null;
        }
        return runUrl + "/job/" + externalJobId.trim();
    }

    static String extractTicketKey(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            Matcher matcher = TICKET_KEY_PATTERN.matcher(candidate);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return null;
    }

    static Integer toInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
