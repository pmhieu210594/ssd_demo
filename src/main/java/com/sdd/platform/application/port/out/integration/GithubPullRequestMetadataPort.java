package com.sdd.platform.application.port.out.integration;

import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.PullRequestGraph;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.PullRequestSummary;

import java.util.List;

public interface GithubPullRequestMetadataPort {

    PullRequestGraph fetchPullRequest(String repositoryFullName, int pullRequestNumber);

    List<PullRequestSummary> listPullRequests(String repositoryFullName, boolean includeClosed);
}
