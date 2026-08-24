package com.sdd.platform.application.port.out.persistence;

import java.util.UUID;

public interface AiFindingStatPort {

    void upsert(AiFindingStatRecord record);

    record AiFindingStatRecord(
            UUID projectId,
            UUID repositoryId,
            UUID ticketId,
            Integer blockerMajorResolvedCount,
            Integer blockerMajorTotalCount,
            Integer aiReviewAdoptedCount,
            Integer aiReviewFindingTotalCount,
            Integer aiReviewValidCount,
            Integer aiReviewFalsePositiveCount,
            Integer aiReviewResolvedCount) {
    }
}
