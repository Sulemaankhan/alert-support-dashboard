package com.support.alert.health;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "support.healthcheck")
public class HealthCheckProperties {

    /**
     * When true, HealthCheck APM is available. Remote scrapes still require a target URL.
     */
    private boolean enabled = true;

    /**
     * Legacy single-target Actuator URL. Used only when {@link #applications} is empty.
     * Prefer {@code support.healthcheck.applications.<id>.environments.<env>.url}.
     */
    private String url = "";

    /** Legacy display / logical service name for the monitored target. */
    private String service = "";

    /** Application id selected when the UI does not pass {@code application}. */
    private String defaultApplication = "";

    /** Environment id selected when the UI does not pass {@code env}. */
    private String defaultEnvironment = "local";

    /** Named applications, each with one or more environments. */
    private Map<String, ApplicationConfig> applications = new LinkedHashMap<>();

    private Duration connectTimeout = Duration.ofSeconds(2);

    private Duration readTimeout = Duration.ofSeconds(5);

    /** Apdex satisfied threshold in milliseconds (tolerating = 4x). */
    private double apdexThresholdMs = 500;

    /** Max URI transactions to scrape/display. */
    private int maxTransactions = 12;

    /**
     * When true, email APM alert metrics to {@link #alertEmailTo} when conditions open
     * (requires {@code spring.mail.*}).
     */
    private boolean alertEmailEnabled = true;

    /** Recipient for APM alert metric emails. */
    private String alertEmailTo = "emailsupportd13@gmail.com";

    /** Optional From override; falls back to {@code spring.mail.username}. */
    private String alertEmailFrom = "";

    /** Minimum minutes between emails for the same alert code. */
    private int alertEmailCooldownMinutes = 15;

    /**
     * How often to evaluate/email when no UI SSE clients are connected (milliseconds).
     * Full realtime tick still uses {@code support.healthcheck.realtime-interval-ms}.
     */
    private long alertEmailPollIntervalMs = 15_000L;

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

    public String getDefaultApplication() {
        return defaultApplication;
    }

    public void setDefaultApplication(String defaultApplication) {
        this.defaultApplication = defaultApplication;
    }

    public String getDefaultEnvironment() {
        return defaultEnvironment;
    }

    public void setDefaultEnvironment(String defaultEnvironment) {
        this.defaultEnvironment = defaultEnvironment;
    }

    public Map<String, ApplicationConfig> getApplications() {
        return applications;
    }

    public void setApplications(Map<String, ApplicationConfig> applications) {
        this.applications = applications != null ? applications : new LinkedHashMap<>();
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

    public boolean isAlertEmailEnabled() {
        return alertEmailEnabled;
    }

    public void setAlertEmailEnabled(boolean alertEmailEnabled) {
        this.alertEmailEnabled = alertEmailEnabled;
    }

    public String getAlertEmailTo() {
        return alertEmailTo;
    }

    public void setAlertEmailTo(String alertEmailTo) {
        this.alertEmailTo = alertEmailTo;
    }

    public String getAlertEmailFrom() {
        return alertEmailFrom;
    }

    public void setAlertEmailFrom(String alertEmailFrom) {
        this.alertEmailFrom = alertEmailFrom;
    }

    public int getAlertEmailCooldownMinutes() {
        return alertEmailCooldownMinutes;
    }

    public void setAlertEmailCooldownMinutes(int alertEmailCooldownMinutes) {
        this.alertEmailCooldownMinutes = alertEmailCooldownMinutes;
    }

    public long getAlertEmailPollIntervalMs() {
        return alertEmailPollIntervalMs;
    }

    public void setAlertEmailPollIntervalMs(long alertEmailPollIntervalMs) {
        this.alertEmailPollIntervalMs = alertEmailPollIntervalMs;
    }

    public boolean isAlertEmailConfigured() {
        return alertEmailEnabled && alertEmailTo != null && !alertEmailTo.isBlank();
    }

    public boolean isRemoteConfigured() {
        if (applications != null) {
            for (ApplicationConfig app : applications.values()) {
                if (app == null || app.getEnvironments() == null) {
                    continue;
                }
                for (EnvironmentConfig env : app.getEnvironments().values()) {
                    if (env != null && env.isEnabled() && env.getUrl() != null && !env.getUrl().isBlank()) {
                        return true;
                    }
                }
            }
        }
        return enabled && url != null && !url.isBlank();
    }

    /** Normalized Actuator base without trailing slash or {@code /*}. */
    public String normalizedActuatorBaseUrl() {
        return normalizeActuatorUrl(url);
    }

    public static String normalizeActuatorUrl(String url) {
        if (url == null) {
            return "";
        }
        String value = url.trim();
        if (value.isBlank()) {
            return "";
        }
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

    public static class ApplicationConfig {
        /** Display name in the HealthCheck application selector. */
        private String name = "";
        private Map<String, EnvironmentConfig> environments = new LinkedHashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, EnvironmentConfig> getEnvironments() {
            return environments;
        }

        public void setEnvironments(Map<String, EnvironmentConfig> environments) {
            this.environments = environments != null ? environments : new LinkedHashMap<>();
        }
    }

    public static class EnvironmentConfig {
        /** Actuator base URL. Blank means scrape this JVM (local). */
        private String url = "";
        private String service = "";
        private String label = "";
        private boolean enabled = true;

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

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
