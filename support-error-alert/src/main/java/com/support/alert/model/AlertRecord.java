package com.support.alert.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class AlertRecord {

    private final UUID id;
    private final String errorDetails;
    private final String message;
    private final String exception;
    private final String functionPath;
    private final String filePath;
    private final String lineNumber;
    private final String serviceName;
    private final AlertSeverity severity;
    private final AlertStatus status;
    private final Instant createdAt;
    private final String jiraBatchId;
    private final List<String> jiraIssueKeys;

    @JsonCreator
    public AlertRecord(
            @JsonProperty("id") UUID id,
            @JsonProperty("errorDetails") String errorDetails,
            @JsonProperty("message") String message,
            @JsonProperty("exception") String exception,
            @JsonProperty("functionPath") String functionPath,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("lineNumber") String lineNumber,
            @JsonProperty("serviceName") String serviceName,
            @JsonProperty("severity") AlertSeverity severity,
            @JsonProperty("status") AlertStatus status,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("jiraBatchId") String jiraBatchId,
            @JsonProperty("jiraIssueKeys") List<String> jiraIssueKeys) {
        this.id = id;
        this.errorDetails = nullToEmpty(errorDetails);
        this.message = nullToEmpty(message);
        this.exception = nullToEmpty(exception);
        this.functionPath = nullToEmpty(functionPath);
        this.filePath = nullToEmpty(filePath);
        this.lineNumber = nullToEmpty(lineNumber);
        this.serviceName = serviceName;
        this.severity = severity;
        this.status = status;
        this.createdAt = createdAt;
        this.jiraBatchId = jiraBatchId != null ? jiraBatchId : "";
        this.jiraIssueKeys = jiraIssueKeys != null
                ? Collections.unmodifiableList(new ArrayList<>(jiraIssueKeys))
                : List.of();
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    public UUID getId() {
        return id;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public String getMessage() {
        return message;
    }

    public String getException() {
        return exception;
    }

    public String getFunctionPath() {
        return functionPath;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getLineNumber() {
        return lineNumber;
    }

    public String getServiceName() {
        return serviceName;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getJiraBatchId() {
        return jiraBatchId;
    }

    public List<String> getJiraIssueKeys() {
        return jiraIssueKeys;
    }

    /**
     * Same alert row with Jira fields replaced after creating/linking an issue.
     */
    public AlertRecord withJiraAssignment(String jiraBatchId, List<String> jiraIssueKeys) {
        return new AlertRecord(
                id,
                errorDetails,
                message,
                exception,
                functionPath,
                filePath,
                lineNumber,
                serviceName,
                severity,
                status,
                createdAt,
                jiraBatchId,
                jiraIssueKeys);
    }

    public AlertRecord withStatus(AlertStatus newStatus) {
        return new AlertRecord(
                id,
                errorDetails,
                message,
                exception,
                functionPath,
                filePath,
                lineNumber,
                serviceName,
                severity,
                newStatus,
                createdAt,
                jiraBatchId,
                jiraIssueKeys);
    }
}
