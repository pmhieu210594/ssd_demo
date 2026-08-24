package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.application.usecase.traceability.TraceabilityModels.ArtifactCoverageRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.CiRunCoverageRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.CommitCoverageRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.EvidenceEventRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.ParsedSectionRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.PullRequestCoverageRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.TicketRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.TraceabilityLinkRow;
import com.sdd.platform.application.usecase.traceability.TraceabilityModels.TraceabilityReviewCommentRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TraceabilityRepositoryPort {

    Optional<TicketRow> findTicket(UUID ticketId);

    List<ArtifactCoverageRow> findArtifacts(UUID ticketId);

    List<PullRequestCoverageRow> findPullRequests(UUID ticketId);

    List<CommitCoverageRow> findCommits(UUID ticketId);

    List<CiRunCoverageRow> findCiRuns(UUID ticketId);

    List<TraceabilityLinkRow> findTraceabilityLinks(UUID ticketId);

    List<EvidenceEventRow> findEvidenceEvents(UUID ticketId);

    List<ParsedSectionRow> findParsedSections(UUID ticketId);

    int findReviewRoundCount(UUID ticketId);

    List<TraceabilityReviewCommentRow> findReviewComments(UUID ticketId);
}
