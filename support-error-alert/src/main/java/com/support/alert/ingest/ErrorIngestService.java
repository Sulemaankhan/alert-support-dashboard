package com.support.alert.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.support.alert.model.AlertRecord;
import com.support.alert.model.AlertSeverity;
import com.support.alert.model.AlertStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ErrorIngestService {

    private static final Logger log = LoggerFactory.getLogger(ErrorIngestService.class);

    private final ObjectMapper objectMapper;

    public ErrorIngestService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses JSON into alert rows. Jira issue keys are empty until created via the dashboard API.
     */
    public List<AlertRecord> parseAlertsFromJson(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            return extractAlerts(root);
        } catch (Exception e) {
            log.warn("Invalid JSON ingest: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    public List<AlertRecord> parseAlertsFromJson(JsonNode root) {
        if (root == null || root.isNull()) {
            log.debug("parseAlertsFromJson: empty JsonNode");
            return new ArrayList<>();
        }
        List<AlertRecord> result = extractAlerts(root);
        log.debug("parseAlertsFromJson: extracted {} alert record(s)", result.size());
        return result;
    }

    private List<AlertRecord> extractAlerts(JsonNode root) {
        List<AlertRecord> out = new ArrayList<>();
        if (root == null || root.isNull()) {
            return out;
        }
        if (root.isArray()) {
            for (JsonNode node : root) {
                out.add(mapOne(node, null));
            }
            return out;
        }
        JsonNode errors = root.path("errors");
        if (errors.isArray()) {
            String serviceFromRoot = text(root, "serviceName", "service", "application");
            for (JsonNode node : errors) {
                out.add(mapOne(node, serviceFromRoot));
            }
            return out;
        }
        out.add(mapOne(root, text(root, "serviceName", "service", "application")));
        return out;
    }

    private AlertRecord mapOne(JsonNode node, String defaultServiceName) {
        String message = firstNonBlank(
                text(node, "errorDetails", "error", "message", "detail", "description", "msg"),
                node.isTextual() ? node.asText() : null
        );
        String stackOrCause = text(node, "stackTrace", "stack", "cause");
        String errorDetails = buildErrorDetails(message, stackOrCause, node);

        String filePath = firstNonBlank(
                text(node, "filePath", "file", "filepath", "sourceFile", "path"));
        String functionPath = firstNonBlank(
                text(node, "functionPath", "function", "method", "handler", "operationId"));

        String serviceName = firstNonBlank(
                text(node, "serviceName", "service", "application"),
                defaultServiceName,
                "unknown-service");

        AlertSeverity severity = parseSeverity(text(node, "severity", "level", "priority"));
        AlertStatus status = parseStatus(text(node, "status", "state"));

        return new AlertRecord(
                UUID.randomUUID(),
                errorDetails,
                functionPath != null ? functionPath : "",
                filePath != null ? filePath : "",
                serviceName,
                severity,
                status,
                Instant.now(),
                "",
                List.of()
        );
    }

    private static String buildErrorDetails(String message, String stackOrCause, JsonNode node) {
        StringBuilder sb = new StringBuilder();
        if (message != null && !message.isEmpty()) {
            sb.append(message.trim());
        }
        if (stackOrCause != null && !stackOrCause.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(stackOrCause.trim());
        }
        if (sb.length() == 0) {
            sb.append(node.toString());
        }
        return sb.toString();
    }

    private static String text(JsonNode node, String... fieldNames) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String name : fieldNames) {
            JsonNode child = node.get(name);
            if (child != null && !child.isNull() && child.isValueNode()) {
                String v = child.asText();
                if (v != null && !v.isEmpty()) {
                    return v;
                }
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v;
            }
        }
        return null;
    }

    private static AlertSeverity parseSeverity(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return AlertSeverity.MEDIUM;
        }
        switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "INFO":
            case "INFORMATIONAL":
                return AlertSeverity.INFO;
            case "LOW":
            case "WARN":
            case "WARNING":
                return AlertSeverity.LOW;
            case "MEDIUM":
            case "MODERATE":
            case "DEFAULT":
                return AlertSeverity.MEDIUM;
            case "HIGH":
            case "ERROR":
            case "ERR":
                return AlertSeverity.HIGH;
            case "CRITICAL":
            case "FATAL":
            case "SEVERE":
                return AlertSeverity.CRITICAL;
            default:
                return AlertSeverity.MEDIUM;
        }
    }

    private static AlertStatus parseStatus(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return AlertStatus.OPEN;
        }
        switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "ACK":
            case "ACKNOWLEDGED":
            case "IN_PROGRESS":
            case "IN PROGRESS":
                return AlertStatus.ACKNOWLEDGED;
            case "RESOLVED":
            case "CLOSED":
            case "FIXED":
            case "DONE":
                return AlertStatus.RESOLVED;
            default:
                return AlertStatus.OPEN;
        }
    }
}
