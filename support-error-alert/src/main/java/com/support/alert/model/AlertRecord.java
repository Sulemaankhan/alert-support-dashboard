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
    private final String functionPath;
    private final String filePath;
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
            @JsonProperty("functionPath") String functionPath,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("serviceName") String serviceName,
            @JsonProperty("severity") AlertSeverity severity,
            @JsonProperty("status") AlertStatus status,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("jiraBatchId") String jiraBatchId,
            @JsonProperty("jiraIssueKeys") List<String> jiraIssueKeys) {
        this.id = id;
        this.errorDetails = errorDetails;
        this.functionPath = functionPath;
        this.filePath = filePath;
        this.serviceName = serviceName;
        this.severity = severity;
        this.status = status;
        this.createdAt = createdAt;
        this.jiraBatchId = jiraBatchId != null ? jiraBatchId : "";
        this.jiraIssueKeys = jiraIssueKeys != null
                ? Collections.unmodifiableList(new ArrayList<>(jiraIssueKeys))
                : List.of();
    }

    public UUID getId() {
        return id;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public String getFunctionPath() {
        return functionPath;
    }

    public String getFilePath() {
        return filePath;
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
                functionPath,
                filePath,
                serviceName,
                severity,
                status,
                createdAt,
                jiraBatchId,
                jiraIssueKeys);
    }
}
