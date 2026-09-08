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
