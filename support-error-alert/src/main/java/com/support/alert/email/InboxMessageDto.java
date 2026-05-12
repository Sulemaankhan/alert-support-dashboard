package com.support.alert.email;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class InboxMessageDto {

    private final String messageId;
    private final String subject;
    private final String from;
    private final String sentAt;
    private final String preview;

    @JsonCreator
    public InboxMessageDto(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("subject") String subject,
            @JsonProperty("from") String from,
            @JsonProperty("sentAt") String sentAt,
            @JsonProperty("preview") String preview) {
        this.messageId = messageId;
        this.subject = subject;
        this.from = from;
        this.sentAt = sentAt;
        this.preview = preview;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSubject() {
        return subject;
    }

    public String getFrom() {
        return from;
    }

    public String getSentAt() {
        return sentAt;
    }

    public String getPreview() {
        return preview;
    }
}
