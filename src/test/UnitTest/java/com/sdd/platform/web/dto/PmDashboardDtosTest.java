package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.AiFindingStatsRow;
import com.sdd.platform.web.dto.PmDashboardDtos.AiFindingStatsDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-AIRKI-9: {@code rate()} is the only place the 5 KPI percentages are
 * computed, and it was previously exercised only indirectly through
 * {@code PmDashboardControllerTest.aiFindingStats_returnsComputedRates}, whose
 * fixture never sends a zero denominator and never sends a non-terminating
 * ratio — so the null branch and the rounding behavior were both completely
 * unverified before this class existed.
 */
class PmDashboardDtosTest {

        private static final UUID REPOSITORY_ID = UUID.randomUUID();

        @Test
        void from_zeroDenominatorForAllFiveKpis_producesNullRatesNotDivideByZeroErrorOrNaN() {
                AiFindingStatsRow row = new AiFindingStatsRow(REPOSITORY_ID, "widget", 0, 0, 0, 0, 0, 0, 0);

                AiFindingStatsDto dto = AiFindingStatsDto.from(row);

                assertThat(dto.blockerMajorResolutionRate())
                                .as("a repository with zero findings recorded yet must report null (\"-\" on FE, "
                                                + "per OI-AIRKI-7), never NaN/Infinity/an ArithmeticException")
                                .isNull();
                assertThat(dto.aiReviewAdoptionRate()).isNull();
                assertThat(dto.aiReviewValidFindingRate()).isNull();
                assertThat(dto.aiFalsePositiveRate()).isNull();
                assertThat(dto.aiFindingResolutionRate()).isNull();
        }

        @Test
        void from_zeroDenominatorOnOnlyTheIndependentBlockerColumn_nullsOnlyThatOneRateNotTheOtherFour() {
                // A-AIRKI-3: blocker_major_total_count is an independent denominator from
                // ai_review_finding_total_count, so a ticket with no Blocker/Major findings
                // yet (0/0) but real adoption/valid/false-positive/resolution data must not
                // have those 4 unrelated rates dragged down to null by the unrelated 0/0.
                AiFindingStatsRow row = new AiFindingStatsRow(REPOSITORY_ID, "widget", 0, 0, 4, 5, 4, 1, 4);

                AiFindingStatsDto dto = AiFindingStatsDto.from(row);

                assertThat(dto.blockerMajorResolutionRate()).isNull();
                assertThat(dto.aiReviewAdoptionRate()).isEqualByComparingTo(BigDecimal.valueOf(80.0));
                assertThat(dto.aiReviewValidFindingRate()).isEqualByComparingTo(BigDecimal.valueOf(80.0));
                assertThat(dto.aiFalsePositiveRate()).isEqualByComparingTo(BigDecimal.valueOf(20.0));
                assertThat(dto.aiFindingResolutionRate()).isEqualByComparingTo(BigDecimal.valueOf(80.0));
        }

        @Test
        void from_nonTerminatingRatio_roundsToOneDecimalPlaceNotTruncatedOrMultiDecimal() {
                // 1/3 = 33.333...%, 2/3 = 66.666...% — both are classic non-terminating
                // binary/decimal fractions that expose truncation-vs-rounding bugs and
                // floating point representation error if computed naively.
                AiFindingStatsRow row = new AiFindingStatsRow(REPOSITORY_ID, "widget", 1, 3, 2, 3, 2, 1, 2);

                AiFindingStatsDto dto = AiFindingStatsDto.from(row);

                assertThat(dto.blockerMajorResolutionRate())
                                .as("1/3 must round to 33.3, not truncate to 33.0 or carry extra decimals")
                                .isEqualByComparingTo(BigDecimal.valueOf(33.3));
                assertThat(dto.aiReviewAdoptionRate())
                                .as("2/3 must round to 66.7, not truncate to 66.6")
                                .isEqualByComparingTo(BigDecimal.valueOf(66.7));
                assertThat(dto.aiFalsePositiveRate())
                                .isEqualByComparingTo(BigDecimal.valueOf(33.3));
        }

        @Test
        void from_exactDivision_producesWholeNumberRate() {
                AiFindingStatsRow row = new AiFindingStatsRow(REPOSITORY_ID, "widget", 3, 4, 5, 5, 5, 0, 5);

                AiFindingStatsDto dto = AiFindingStatsDto.from(row);

                assertThat(dto.blockerMajorResolutionRate()).isEqualByComparingTo(BigDecimal.valueOf(75.0));
                assertThat(dto.aiReviewAdoptionRate()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
                assertThat(dto.aiFalsePositiveRate()).isEqualByComparingTo(BigDecimal.valueOf(0.0));
        }

        @Test
        void from_mapsRepositoryIdAndNameThrough() {
                AiFindingStatsRow row = new AiFindingStatsRow(REPOSITORY_ID, "widget", 1, 1, 1, 1, 1, 0, 1);

                AiFindingStatsDto dto = AiFindingStatsDto.from(row);

                assertThat(dto.repositoryId()).isEqualTo(REPOSITORY_ID);
                assertThat(dto.repositoryName()).isEqualTo("widget");
        }
}
