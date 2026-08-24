package com.sdd.platform.application.port.out.integration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface GithubPullRequestFilesPort {

    List<String> listChangedFilePaths(String repositoryFullName, int pullRequestNumber);

    Optional<OffsetDateTime> resolveLastCommitAt(String repositoryFullName, int pullRequestNumber);
}
