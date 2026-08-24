package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.application.usecase.governance.ExceptionKpiService.ExceptionKpiResult;

import java.util.UUID;

public interface ExceptionKpiRepositoryPort {

    ExceptionKpiResult findExceptionKpi(UUID ticketId);
}
