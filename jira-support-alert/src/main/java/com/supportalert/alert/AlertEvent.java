package com.supportalert.alert;

import java.time.Instant;
import java.util.Objects;

public class AlertEvent {

    private final AlertType type;
    private final String message;
    private final Long issueId;
    private final String detail;
    private final Instant timestamp;

    public AlertEvent(AlertType type, String message, Long issueId, String detail) {
        this.type = Objects.requireNonNull(type);
        this.message = message;
        this.issueId = issueId;
        this.detail = detail;
        this.timestamp = Instant.now();
    }

    public AlertType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Long getIssueId() {
        return issueId;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
