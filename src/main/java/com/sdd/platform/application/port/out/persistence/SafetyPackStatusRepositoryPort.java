package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.domain.model.SafetyPackStatus;

import java.util.List;

public interface SafetyPackStatusRepositoryPort {

    SafetyPackStatus save(SafetyPackStatus status);

    List<SafetyPackStatus> findRecent(int limit);
}
