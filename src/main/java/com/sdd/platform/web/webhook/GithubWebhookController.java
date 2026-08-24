package com.sdd.platform.web.webhook;

import com.sdd.platform.application.usecase.ingestion.GithubWebhookService;
import com.sdd.platform.application.usecase.ingestion.GithubWorkflowJobWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Receives webhook POSTs from GitHub.
 *
 * Why {@code byte[]} for the body? — HMAC is computed on the EXACT bytes GitHub
 * signed. If we let Spring parse into a JsonNode first, re-serialization changes
 * whitespace/key-order and the signature won't match.
 *
 * Returns 2xx as fast as possible (GitHub considers > 10s a failure and retries).
 * Heavy work, if needed, should be enqueued — current handler is light enough to
 * stay synchronous.
 */
@RestController
@RequestMapping("/api/v1/webhooks/github")
public class GithubWebhookController {

    private final GithubWebhookService service;
    private final GithubWorkflowJobWebhookService workflowJobService;

    public GithubWebhookController(GithubWebhookService service,
                                   GithubWorkflowJobWebhookService workflowJobService) {
        this.service = service;
        this.workflowJobService = workflowJobService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> receive(
            @RequestBody byte[] body,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String delivery
    ) {
        try {
            if ("workflow_job".equals(event)) {
                var r = workflowJobService.handle(body, signature, event, delivery);
                return ResponseEntity.ok(Map.of(
                        "handled", r.handled(),
                        "recordsAffected", r.recordsAffected()
                ));
            }
            GithubWebhookService.Result r = service.handle(body, signature, event, delivery);
            return ResponseEntity.ok(Map.of(
                    "handled", r.handled(),
                    "recordsAffected", r.recordsAffected()
            ));
        } catch (SecurityException e) {
            // Don't echo `e.getMessage()` — could leak which check failed.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "signature_invalid"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "bad_payload"));
        }
    }
}
