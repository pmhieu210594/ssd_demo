package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.docparse.DocParseModels.ParseMode;
import com.sdd.platform.application.usecase.docparse.ImplPlanParseService;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.web.dto.ImplPlanParseDtos.ImplPlanParseRequestDto;
import com.sdd.platform.web.dto.ImplPlanParseDtos.ImplPlanParseResultDto;
import com.sdd.platform.web.dto.ImplPlanParseDtos.ImplPlanParseSnapshotDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demo/impl-plan-parses")
public class ImplPlanParseController {

    private final ImplPlanParseService service;

    public ImplPlanParseController(ImplPlanParseService service) {
        this.service = service;
    }

    @PostMapping
    public ImplPlanParseResultDto parse(@RequestBody @Valid ImplPlanParseRequestDto request) {
        return ImplPlanParseResultDto.from(service.parseAndStore(request.toRequest()));
    }

    @GetMapping("/tickets/{ticketId}")
    public List<ImplPlanParseSnapshotDto> listSnapshots(
            @PathVariable UUID ticketId,
            @RequestParam(defaultValue = "DRAFT") ParseMode parseMode,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return service.recentSnapshots(ticketId, parseMode, limit).stream()
                .map(ImplPlanParseSnapshotDto::from)
                .toList();
    }

    @GetMapping("/snapshots/{snapshotId}")
    public ImplPlanParseResultDto getSnapshot(@PathVariable UUID snapshotId) {
        return service.detail(snapshotId)
                .map(ImplPlanParseResultDto::from)
                .orElseThrow(() -> new NotFoundException("DOC_PARSE_SNAPSHOT_NOT_FOUND"));
    }
}
