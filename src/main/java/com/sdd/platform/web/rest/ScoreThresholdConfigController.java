package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.UpsertScoreThreshold;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.web.dto.ScoreThresholdConfigDtos.SaveScoreThresholdsRequest;
import com.sdd.platform.web.dto.ScoreThresholdConfigDtos.ScoreThresholdDto;
import com.sdd.platform.web.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only Score Threshold Configuration screen (HD-THRESHOLD-CONFIG-5). Both endpoints are
 * gated by {@link ScoreThresholdConfigService}'s own {@code requireAdmin} check.
 */
@RestController
@RequestMapping("/api/v1/score-thresholds")
public class ScoreThresholdConfigController {

    private final ScoreThresholdConfigService service;

    public ScoreThresholdConfigController(ScoreThresholdConfigService service) {
        this.service = service;
    }

    @GetMapping
    public List<ScoreThresholdDto> list(@CurrentUser AppUser caller) {
        return service.list(caller).stream().map(ScoreThresholdDto::from).toList();
    }

    @PostMapping
    public List<ScoreThresholdDto> save(@RequestBody SaveScoreThresholdsRequest request, @CurrentUser AppUser caller) {
        List<UpsertScoreThreshold> rows = request.thresholds() == null
                ? List.of()
                : request.thresholds().stream().map(dto -> dto.toModel()).toList();
        return service.save(rows, caller).stream().map(ScoreThresholdDto::from).toList();
    }
}
