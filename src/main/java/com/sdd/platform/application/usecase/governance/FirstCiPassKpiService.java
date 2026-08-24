package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.CiRunRepositoryPort;
import com.sdd.platform.application.usecase.ingestion.CiRunModels.CiRunMetadataView;
import com.sdd.platform.domain.model.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class FirstCiPassKpiService {

    private final CiRunRepositoryPort repositoryPort;

    public FirstCiPassKpiService(CiRunRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Transactional(readOnly = true)
    public Optional<FirstCiPassResult> computeForTicket(UUID ticketId, AppUser caller) {
        requireAdminOrPm(caller);
        if (ticketId == null) {
            return Optional.empty();
        }
        return repositoryPort.findFirstCiRunByTicketId(ticketId)
                .map(run -> toResult(run, null));
    }

    private static FirstCiPassResult toResult(CiRunMetadataView run, UUID pullRequestId) {
        boolean passed = "SUCCESS".equalsIgnoreCase(run.status());
        return new FirstCiPassResult(
                run.ciRunId(),
                pullRequestId,
                run.status(),
                passed,
                run.startedAt()
        );
    }

    private static void requireAdminOrPm(AppUser caller) {
        if (caller == null) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
        AppUser.Role role = caller.getRole();
        if (role != AppUser.Role.ADMIN) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }

    public record FirstCiPassResult(
            UUID ciRunId,
            UUID pullRequestId,
            String firstRunStatus,
            boolean firstPassSuccess,
            OffsetDateTime firstRunStartedAt
    ) {}
}
