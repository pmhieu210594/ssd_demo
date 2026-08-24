package com.sdd.platform.infrastructure.gitlocal;

import com.sdd.platform.application.port.out.integration.EvidenceConnectorPort;
import com.sdd.platform.application.usecase.governance.SafetyPackService;
import com.sdd.platform.config.AppProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class SafetyPackLocalConnector implements EvidenceConnectorPort {

    private final AppProperties props;
    private final SafetyPackService safetyPackService;

    public SafetyPackLocalConnector(AppProperties props, SafetyPackService safetyPackService) {
        this.props = props;
        this.safetyPackService = safetyPackService;
    }

    @Override
    public String name() {
        return "safety_pack_local";
    }

    @Override
    public ConnectorResult sync(Long projectId) {
        Path root = Path.of(props.connectors().gitLocal().rootPath()).toAbsolutePath().normalize();
        int ingested = safetyPackService.scanFromRoot(root);
        return ConnectorResult.of(ingested);
    }
}
