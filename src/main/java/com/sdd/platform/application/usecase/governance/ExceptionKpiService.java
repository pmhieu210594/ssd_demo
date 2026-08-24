package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.ExceptionKpiRepositoryPort;
import com.sdd.platform.domain.model.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ExceptionKpiService {

    private final ExceptionKpiRepositoryPort repositoryPort;

    public ExceptionKpiService(ExceptionKpiRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Transactional(readOnly = true)
    public ExceptionKpiResult computeForTicket(UUID ticketId, AppUser caller) {
        requireAdminOrPm(caller);
        if (ticketId == null) {
            return new ExceptionKpiResult(null, 0, 0, 0, false, List.of());
        }
        return repositoryPort.findExceptionKpi(ticketId);
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

    public record ExceptionSummary(
            UUID exceptionId,
            String exceptionType,
            boolean reasonPresent,
            boolean approved,
            String followUpStatus,
            String sourceSection,
            String linkedReportPath
    ) {}

    public record ExceptionKpiResult(
            UUID ticketId,
            int totalExceptions,
            int openExceptions,
            int approvedExceptions,
            boolean hasExplicitExceptions,
            List<ExceptionSummary> exceptions
    ) {}
}
