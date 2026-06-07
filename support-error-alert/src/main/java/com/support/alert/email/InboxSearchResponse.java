package com.support.alert.email;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class InboxSearchResponse {

    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";

    private final String status;
    private final String querySubject;
    private final List<InboxMessageDto> messages;
    private final String detail;

    public InboxSearchResponse(String status, String querySubject, List<InboxMessageDto> messages, String detail) {
        this.status = status;
        this.querySubject = querySubject;
        this.messages = messages != null ? messages : Collections.emptyList();
        this.detail = detail;
    }

    public static InboxSearchResponse disabled(String subject) {
        return new InboxSearchResponse(
                STATUS_DISABLED,
                subject,
                Collections.emptyList(),
                "IMAP inbox lookup is disabled. Set support.email.imap.enabled=true and configure host, username, and password.");
    }

    public static InboxSearchResponse notConfigured(String subject) {
        return notConfigured(
                subject,
                "IMAP is enabled but host, username, or password is missing. Check support.email.imap.* in application-local.properties.");
    }

    public static InboxSearchResponse notConfigured(String subject, String detail) {
        return new InboxSearchResponse(STATUS_DISABLED, subject, Collections.emptyList(), detail);
    }

    public static InboxSearchResponse ok(String subject, List<InboxMessageDto> messages) {
        return new InboxSearchResponse(STATUS_OK, subject, messages, null);
    }

    public static InboxSearchResponse error(String subject, String detail) {
        return new InboxSearchResponse(STATUS_ERROR, subject, Collections.emptyList(), detail);
    }

    public String getStatus() {
        return status;
    }

    public String getQuerySubject() {
        return querySubject;
    }

    public List<InboxMessageDto> getMessages() {
        return messages;
    }

    public String getDetail() {
        return detail;
    }
}
