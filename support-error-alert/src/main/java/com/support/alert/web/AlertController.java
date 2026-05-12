package com.support.alert.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.support.alert.ingest.ErrorIngestService;
import com.support.alert.jira.JiraIssueCreateService;
import com.support.alert.jira.JiraIssueCreateService.CreatedJiraIssue;
import com.support.alert.model.AlertRecord;
import com.support.alert.model.AlertStatus;
import com.support.alert.store.InMemoryAlertStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    private final ErrorIngestService ingestService;
    private final InMemoryAlertStore alertStore;
    private final JiraIssueCreateService jiraIssueCreateService;

    public AlertController(
            ErrorIngestService ingestService,
            InMemoryAlertStore alertStore,
            JiraIssueCreateService jiraIssueCreateService) {
        this.ingestService = ingestService;
        this.alertStore = alertStore;
        this.jiraIssueCreateService = jiraIssueCreateService;
    }

    @GetMapping
    public List<AlertRecord> list() {
        List<AlertRecord> all = alertStore.findAllInArrivalOrder();
        log.debug("GET /api/alerts - returning {} record(s)", all.size());
        return all;
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAll() {
        int previous = alertStore.size();
        alertStore.clearAll();
        log.warn("DELETE /api/alerts - cleared {} alert(s)", previous);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ingest")
    public ResponseEntity<List<AlertRecord>> ingest(@RequestBody(required = false) JsonNode body) {
        if (body == null) {
            log.warn("POST /api/alerts/ingest - rejected: empty body");
            return ResponseEntity.badRequest().build();
        }
        List<AlertRecord> parsed = ingestService.parseAlertsFromJson(body);
        alertStore.addAll(parsed);
        log.info("POST /api/alerts/ingest - stored {} new alert(s)", parsed.size());
        return ResponseEntity.ok(parsed);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<AlertRecord> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String statusRaw = body.get("status");
        if (statusRaw == null || statusRaw.trim().isEmpty()) {
            log.warn("PUT /api/alerts/{}/status - rejected: missing status", id);
            return ResponseEntity.badRequest().build();
        }
        try {
            AlertStatus status = AlertStatus.valueOf(statusRaw.trim().toUpperCase());
            return alertStore.updateStatus(id, status)
                    .map(record -> {
                        log.info("PUT /api/alerts/{}/status - updated to {}", id, status);
                        return ResponseEntity.ok(record);
                    })
                    .orElseGet(() -> {
                        log.warn("PUT /api/alerts/{}/status — alert not found", id);
                        return ResponseEntity.notFound().build();
                    });
        } catch (IllegalArgumentException ex) {
            log.warn("PUT /api/alerts/{}/status - invalid status: {}", id, statusRaw);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Creates a Jira issue from this alert: {@link AlertRecord#getErrorDetails()} as description,
     * {@link com.support.alert.model.AlertSeverity} mapped to Jira priority.
     * Optional JSON body: {@code { "summary": "override title" }}.
     */
    @PostMapping("/{id}/jira/issue")
    public ResponseEntity<Map<String, Object>> createJiraIssue(
            @PathVariable UUID id,
            @RequestBody(required = false) JsonNode body) {
        Optional<AlertRecord> alertOpt = alertStore.findById(id);
        if (alertOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AlertRecord alert = alertOpt.get();
        if (!alert.getJiraIssueKeys().isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "This alert already has a linked Jira issue."));
        }
        Map<String, String> overrides = Map.of();
        if (body != null && body.isObject()) {
            String summary = textField(body, "summary");
            if (summary != null) {
                overrides = Map.of("summary", summary);
            }
        }
        try {
            CreatedJiraIssue created = jiraIssueCreateService.createIssue(alert, overrides);
            return alertStore.attachJiraIssue(id, created.getIssueKey())
                    .map(updated -> {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("issueKey", created.getIssueKey());
                        payload.put("issueSelf", created.getIssueSelf());
                        payload.put("alert", updated);
                        return ResponseEntity.ok(payload);
                    })
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
        }
    }

    private static String textField(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isTextual()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }
}
