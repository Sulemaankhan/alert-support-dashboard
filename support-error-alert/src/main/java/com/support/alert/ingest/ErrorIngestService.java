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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ErrorIngestService {

    private static final Logger log = LoggerFactory.getLogger(ErrorIngestService.class);

    private static final Pattern EXCEPTION_PREFIX = Pattern.compile(
            "^([A-Z][\\w.$]*(?:Exception|Error))(?:\\s*[:—\\-]\\s*|\\s+when\\s+|\\s+at\\s+|\\s+)(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCEPTION_ONLY = Pattern.compile("^([A-Z][\\w.$]*(?:Exception|Error))$");
    private static final Pattern LINE_IN_FUNCTION = Pattern.compile(":(\\d+)(?:\\)|\\s|,|$)");

    private final ObjectMapper objectMapper;

    public ErrorIngestService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses JSON into alert rows. Jira issue keys are empty until created via the dashboard API.
     */
    public List<AlertRecord> parseAlertsFromJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return new ArrayList<>();
        }
        String trimmed = rawJson.trim();
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            return extractAlerts(root);
        } catch (Exception singleDocumentFailure) {
            List<AlertRecord> fromLines = parseJsonLines(trimmed);
            if (fromLines.isEmpty()) {
                log.warn("Invalid JSON ingest: {}", singleDocumentFailure.getMessage());
                throw new IllegalArgumentException("Invalid JSON: " + singleDocumentFailure.getMessage(), singleDocumentFailure);
            }
            log.debug("parseAlertsFromJson: parsed {} alert record(s) from JSON lines", fromLines.size());
            return fromLines;
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
            String serviceFromRoot = firstNonBlank(
                    text(root, "serviceName", "application"),
                    nestedText(root, "service", "name"));
            for (JsonNode node : errors) {
                out.add(mapOne(node, serviceFromRoot));
            }
            return out;
        }
        out.add(mapOne(root, firstNonBlank(
                text(root, "serviceName", "application"),
                nestedText(root, "service", "name"))));
        return out;
    }

    private List<AlertRecord> parseJsonLines(String raw) {
        List<AlertRecord> out = new ArrayList<>();
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                out.add(mapOne(node, nestedText(node, "service", "name")));
            } catch (Exception ignored) {
                // skip malformed lines in JSONL log files
            }
        }
        out.sort((left, right) -> Integer.compare(alertImportance(right), alertImportance(left)));
        return out;
    }

    private static int alertImportance(AlertRecord alert) {
        int score = 0;
        if (alert.getException() != null && !alert.getException().isBlank()) {
            score += 20;
        }
        switch (alert.getSeverity()) {
            case CRITICAL -> score += 15;
            case HIGH -> score += 10;
            case MEDIUM -> score += 5;
            case LOW -> score += 2;
            default -> {
            }
        }
        return score;
    }

    private AlertRecord mapOne(JsonNode node, String defaultServiceName) {
        String explicitMessage = text(node, "message", "detail", "description", "msg");
        String explicitException = firstNonBlank(
                text(node, "exception", "exceptionType", "exceptionName", "errorType", "type"),
                nestedErrorSummary(node));
        String errorField = text(node, "error", "errorDetails");
        String stackOrCause = firstNonBlank(
                text(node, "stackTrace", "stack", "cause"),
                nestedText(node, "error", "stack_trace"));

        String message = explicitMessage;
        String exception = explicitException;

        if (message == null && errorField != null) {
            MessageExceptionPair parsed = splitMessageAndException(errorField);
            message = firstNonBlank(parsed.message(), errorField);
            if (exception == null) {
                exception = parsed.exception();
            }
        }

        message = message != null ? message.trim() : "";
        exception = exception != null ? exception.trim() : "";

        String filePath = firstNonBlank(text(node, "filePath", "file", "filepath", "sourceFile", "path"));
        String functionPath = firstNonBlank(
                text(node, "functionPath", "function", "method", "handler", "operationId", "functionName"),
                buildFunctionPath(node, filePath));
        String lineNumber = resolveLineNumber(
                text(node, "lineNumber", "line", "lineNo", "lineno", "line_number"),
                functionPath);

        String errorDetails = buildErrorDetails(message, exception, stackOrCause, node);

        String serviceName = firstNonBlank(
                text(node, "serviceName", "application"),
                nestedText(node, "service", "name"),
                defaultServiceName,
                "unknown-service");

        AlertSeverity severity = parseSeverity(firstNonBlank(
                text(node, "severity", "level", "priority"),
                nestedText(node, "log", "level")));
        AlertStatus status = parseStatus(text(node, "status", "state"));

        return new AlertRecord(
                UUID.randomUUID(),
                errorDetails,
                message,
                exception,
                functionPath != null ? functionPath : "",
                filePath != null ? filePath : "",
                lineNumber,
                serviceName,
                severity,
                status,
                Instant.now(),
                "",
                List.of());
    }

    private static MessageExceptionPair splitMessageAndException(String combined) {
        if (combined == null || combined.isBlank()) {
            return new MessageExceptionPair("", "");
        }
        String s = combined.trim();
        Matcher prefixed = EXCEPTION_PREFIX.matcher(s);
        if (prefixed.matches()) {
            return new MessageExceptionPair(prefixed.group(2).trim(), prefixed.group(1));
        }
        Matcher only = EXCEPTION_ONLY.matcher(s);
        if (only.matches()) {
            return new MessageExceptionPair("", only.group(1));
        }
        return new MessageExceptionPair(s, "");
    }

    private static String resolveLineNumber(String explicitLine, String functionPath) {
        if (explicitLine != null && !explicitLine.isBlank()) {
            return explicitLine.trim();
        }
        if (functionPath != null && !functionPath.isBlank()) {
            Matcher matcher = LINE_IN_FUNCTION.matcher(functionPath);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    private static String buildErrorDetails(String message, String exception, String stackOrCause, JsonNode node) {
        StringBuilder sb = new StringBuilder();
        if (exception != null && !exception.isEmpty()) {
            sb.append(exception.trim());
        }
        if (message != null && !message.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(": ");
            }
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

    private static String buildFunctionPath(JsonNode node, String filePath) {
        String functionName = text(node, "functionName", "method", "function");
        if (functionName == null || functionName.isBlank()) {
            return null;
        }
        String className = firstNonBlank(filePath, text(node, "filePath", "file", "filepath", "path"));
        String fileName = text(node, "fileName");
        String lineNumber = text(node, "lineNumber", "line", "lineNo", "lineno", "line_number");

        String location = "";
        if (fileName != null && lineNumber != null) {
            location = "(" + fileName + ":" + lineNumber + ")";
        } else if (fileName != null) {
            location = "(" + fileName + ")";
        }

        if (className != null && !className.isBlank()) {
            return className + "." + functionName + location;
        }
        return functionName + location;
    }

    private static String nestedErrorSummary(JsonNode node) {
        String type = nestedText(node, "error", "type");
        String message = nestedText(node, "error", "message");
        if (type != null && message != null) {
            return type + ": " + message;
        }
        return firstNonBlank(type, message);
    }

    private static String nestedText(JsonNode node, String... path) {
        if (node == null || !node.isObject() || path == null || path.length == 0) {
            return null;
        }
        JsonNode current = node;
        for (String segment : path) {
            if (current == null) {
                return null;
            }
            current = current.get(segment);
        }
        if (current != null && !current.isNull() && current.isValueNode()) {
            String value = current.asText();
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
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

    private record MessageExceptionPair(String message, String exception) {}
}
