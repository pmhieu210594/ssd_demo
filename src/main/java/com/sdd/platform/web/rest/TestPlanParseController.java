package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseMode;
import com.sdd.platform.application.usecase.docparse.TestPlanParseService;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.web.dto.TestDocParseDtos.TestArtifactParseResultDto;
import com.sdd.platform.web.dto.TestDocParseDtos.TestArtifactParseSnapshotDto;
import com.sdd.platform.web.dto.TestDocParseDtos.TestPlanParseRequestDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demo/test-plan-parses")
public class TestPlanParseController {

    private final TestPlanParseService service;

    public TestPlanParseController(TestPlanParseService service) {
        this.service = service;
    }

    @PostMapping
    public TestArtifactParseResultDto parse(@RequestBody @Valid TestPlanParseRequestDto request) {
        return TestArtifactParseResultDto.from(service.parseAndStore(request.toRequest()));
    }

    @GetMapping("/tickets/{ticketId}")
    public List<TestArtifactParseSnapshotDto> listSnapshots(
            @PathVariable UUID ticketId,
            @RequestParam(defaultValue = "DRAFT") ParseMode parseMode,
            @RequestParam(defaultValue = "20") int limit) {
        return service.recentSnapshots(ticketId, parseMode, limit).stream()
                .map(TestArtifactParseSnapshotDto::from)
                .toList();
    }

    @GetMapping("/snapshots/{snapshotId}")
    public TestArtifactParseResultDto getSnapshot(@PathVariable UUID snapshotId) {
        return service.detail(snapshotId)
                .map(TestArtifactParseResultDto::from)
                .orElseThrow(() -> new NotFoundException("TEST_PLAN_PARSE_SNAPSHOT_NOT_FOUND"));
    }
}
