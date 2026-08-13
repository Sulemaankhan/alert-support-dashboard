package com.support.alert.health;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "support.healthcheck")
public class HealthCheckProperties {

    /**
     * When true and {@link #url} is set, HealthCheck APM scrapes the remote Actuator.
     */
    private boolean enabled = true;

    /**
     * Remote Actuator base URL, e.g. {@code http://localhost:4040/actuator}
     * or {@code http://localhost:4040/actuator/*}.
     */
    private String url = "";

    /** Display / logical service name for the monitored target. */
    private String service = "";

    private Duration connectTimeout = Duration.ofSeconds(2);

    private Duration readTimeout = Duration.ofSeconds(5);

    /** Apdex satisfied threshold in milliseconds (tolerating = 4x). */
    private double apdexThresholdMs = 500;

    /** Max URI transactions to scrape/display. */
    private int maxTransactions = 12;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public double getApdexThresholdMs() {
        return apdexThresholdMs;
    }

    public void setApdexThresholdMs(double apdexThresholdMs) {
        this.apdexThresholdMs = apdexThresholdMs;
    }

    public int getMaxTransactions() {
        return maxTransactions;
    }

    public void setMaxTransactions(int maxTransactions) {
        this.maxTransactions = maxTransactions;
    }

    public boolean isRemoteConfigured() {
        return enabled && url != null && !url.isBlank();
    }

    /** Normalized Actuator base without trailing slash or {@code /*}. */
    public String normalizedActuatorBaseUrl() {
        if (url == null) {
            return "";
        }
        String value = url.trim();
        if (value.endsWith("/*")) {
            value = value.substring(0, value.length() - 2);
        } else if (value.endsWith("*")) {
            value = value.substring(0, value.length() - 1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public String resolvedServiceName(String fallback) {
        if (service != null && !service.isBlank()) {
            return service.trim();
        }
        return fallback;
    }
}
