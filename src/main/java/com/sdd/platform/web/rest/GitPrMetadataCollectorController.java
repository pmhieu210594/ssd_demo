package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.RepositoryScanRequest;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.GitPrMetadataCollectorDtos;
import com.sdd.platform.web.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping
public class GitPrMetadataCollectorController {

    private static final String ADMIN_ALIAS_PATH = "/api/v1/admin/git-pr-metadata-collector";
    private static final String REPOSITORY_PATH = "/api/v1/repositories/{repositoryId}/git-pr-metadata/collect";
    private static final String PR_PATH = "/api/v1/repositories/{repositoryId}/pull-requests/{prNumber}/git-pr-metadata/collect";

    private final GitPrMetadataCollectorService service;

    public GitPrMetadataCollectorController(GitPrMetadataCollectorService service) {
        this.service = service;
    }

    @PostMapping(ADMIN_ALIAS_PATH)
    public GitPrMetadataCollectorDtos.CollectResponse collectAdmin(@Valid @RequestBody GitPrMetadataCollectorDtos.CollectRequest request,
                                                                    @CurrentUser AppUser caller) {
        return collectInternal(request, caller);
    }

    @PostMapping(REPOSITORY_PATH)
    public GitPrMetadataCollectorDtos.CollectResponse collectRepository(@PathVariable UUID repositoryId,
                                                                        @Valid @RequestBody(required = false) GitPrMetadataCollectorDtos.CollectRequest request,
                                                                        @CurrentUser AppUser caller) {
        GitPrMetadataCollectorDtos.CollectRequest effective = request == null
                ? new GitPrMetadataCollectorDtos.CollectRequest(repositoryId, null, null, null, false)
                : new GitPrMetadataCollectorDtos.CollectRequest(repositoryId, request.prNumber(), request.fromDate(), request.toDate(), request.includeClosed());
        return collectInternal(effective, caller);
    }

    @PostMapping(PR_PATH)
    public GitPrMetadataCollectorDtos.CollectResponse collectPullRequest(@PathVariable UUID repositoryId,
                                                                         @PathVariable Integer prNumber,
                                                                         @Valid @RequestBody(required = false) GitPrMetadataCollectorDtos.CollectRequest request,
                                                                         @CurrentUser AppUser caller) {
        GitPrMetadataCollectorDtos.CollectRequest effective = request == null
                ? new GitPrMetadataCollectorDtos.CollectRequest(repositoryId, prNumber, null, null, true)
                : new GitPrMetadataCollectorDtos.CollectRequest(repositoryId, prNumber, request.fromDate(), request.toDate(), request.includeClosed());
        return collectInternal(effective, caller);
    }

    private GitPrMetadataCollectorDtos.CollectResponse collectInternal(GitPrMetadataCollectorDtos.CollectRequest request, AppUser caller) {
        var result = service.collectManual(new RepositoryScanRequest(
                request.repositoryId(),
                request.prNumber(),
                request.fromDate(),
                request.toDate(),
                request.includeClosed(),
                null,
                null
        ), caller);
        return GitPrMetadataCollectorDtos.CollectResponse.from(result);
    }
}
