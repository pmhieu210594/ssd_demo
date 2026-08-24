package com.sdd.platform.application.port.out.integration;

public interface EvidenceConnectorPort {

    String name();

    ConnectorResult sync(Long projectId);

    record ConnectorResult(int ingested) {
        public static ConnectorResult of(int ingested) {
            return new ConnectorResult(ingested);
        }
    }
}
