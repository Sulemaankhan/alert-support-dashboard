package com.support.alert.health;

/**
 * One selectable HealthCheck target: an application in a specific environment.
 */
public record HealthTarget(
        String applicationId,
        String applicationName,
        String environmentId,
        String environmentLabel,
        String url,
        String serviceName
) {
    public String key() {
        return applicationId + ":" + environmentId;
    }

    public boolean isRemote() {
        return url != null && !url.isBlank();
    }

    /**
     * This dashboard process: blank URL, or an Actuator URL on this host and {@code server.port}.
     * Self-scrape over HTTP is empty because metric endpoints require a session.
     */
    public boolean usesLocalJvm(int serverPort) {
        if (!isRemote()) {
            return true;
        }
        String base = normalizedActuatorBaseUrl();
        if (!SameHostJvmLocator.isSameHost(base)) {
            return false;
        }
        int port = SameHostJvmLocator.portOf(base);
        return port > 0 && port == serverPort;
    }

    public String normalizedActuatorBaseUrl() {
        return HealthCheckProperties.normalizeActuatorUrl(url);
    }

    public String resolvedServiceName(String fallback) {
        if (serviceName != null && !serviceName.isBlank()) {
            return serviceName.trim();
        }
        if (applicationName != null && !applicationName.isBlank()) {
            return applicationName.trim();
        }
        return fallback;
    }
}
