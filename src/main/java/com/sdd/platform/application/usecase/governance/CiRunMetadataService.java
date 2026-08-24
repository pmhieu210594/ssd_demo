package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.CiRunRepositoryPort;
import com.sdd.platform.application.usecase.ingestion.CiRunModels.CiRunMetadataView;
import com.sdd.platform.domain.model.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CiRunMetadataService {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;

    private final CiRunRepositoryPort repositoryPort;

    public CiRunMetadataService(CiRunRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Transactional(readOnly = true)
    public List<CiRunMetadataView> recent(int limit, AppUser caller) {
        requireAdmin(caller);
        return repositoryPort.findRecentCiRuns(normalizeLimit(limit));
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static void requireAdmin(AppUser caller) {
        if (caller == null || caller.getRole() != AppUser.Role.ADMIN) {
            throw new ForbiddenException("Component.Permission.Denied");
        }
    }
}
