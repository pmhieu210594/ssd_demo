package com.sdd.platform.application.usecase.docparse;

import com.sdd.platform.application.port.out.persistence.DocParsePersistencePort;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseField;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseMode;
import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseSnapshot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TestArtifactPairViewService {

        private final DocParsePersistencePort port;

        public TestArtifactPairViewService(@Qualifier("testPlanDocParse") DocParsePersistencePort port) {
                this.port = port;
        }

        @Transactional(readOnly = true)
        public PairView getPairView(UUID ticketId, ParseMode parseMode) {
                Optional<ParseSnapshot> testPlan = port.findLatestSnapshot(
                                ticketId, TestPlanParseService.DOCUMENT_TYPE, parseMode);
                Optional<ParseSnapshot> testResults = port.findLatestSnapshot(
                                ticketId, TestResultsParseService.DOCUMENT_TYPE, parseMode);

                List<ParseField> testPlanFields = testPlan
                                .map(s -> port.findSections(s.artifactSnapshotId()))
                                .orElse(List.of());
                List<ParseField> testResultsFields = testResults
                                .map(s -> port.findSections(s.artifactSnapshotId()))
                                .orElse(List.of());

                boolean pairViewReady = testPlan.isPresent()
                                && testResults.isPresent()
                                && "SUCCESS".equals(testPlan.get().parseStatus())
                                && "SUCCESS".equals(testResults.get().parseStatus());

                return new PairView(
                                ticketId,
                                testPlan.orElse(null),
                                testPlanFields,
                                testResults.orElse(null),
                                testResultsFields,
                                pairViewReady);
        }

        public record PairView(
                        UUID ticketId,
                        ParseSnapshot testPlanSnapshot,
                        List<ParseField> testPlanFields,
                        ParseSnapshot testResultsSnapshot,
                        List<ParseField> testResultsFields,
                        boolean pairViewReady) {
        }
}
