package com.support.alert.email;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class InboxSearchRequest {

    private final String email;
    private final String password;
    private final String subject;
    private final String host;
    private final Integer port;

    @JsonCreator
    public InboxSearchRequest(
            @JsonProperty("email") String email,
            @JsonProperty("password") String password,
            @JsonProperty("subject") String subject,
            @JsonProperty("host") String host,
            @JsonProperty("port") Integer port) {
        this.email = email;
        this.password = password;
        this.subject = subject;
        this.host = host;
        this.port = port;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getSubject() {
        return subject;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }
}
