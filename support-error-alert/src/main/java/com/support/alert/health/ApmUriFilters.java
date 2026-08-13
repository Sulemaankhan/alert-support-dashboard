package com.support.alert.health;

/** Filters infrastructure / self-scrape URIs out of APM transaction and request views. */
final class ApmUriFilters {

    private ApmUriFilters() {
    }

    static boolean isNoiseUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return true;
        }
        String value = uri.trim();
        if ("ROOT".equals(value) || "/**".equals(value) || "/".equals(value)) {
            return true;
        }
        String lower = value.toLowerCase();
        if (lower.startsWith("/actuator") || lower.contains("/actuator/")) {
            return true;
        }
        // Spring template for scraped metric endpoints — our HealthCheck APM hammered this.
        if (lower.contains("{requiredmetricname}") || lower.contains("requiredmetricname")) {
            return true;
        }
        if (lower.startsWith("/api/health") || lower.contains("/api/health/")) {
            return true;
        }
        if (lower.contains("swagger") || lower.contains("api-docs") || lower.contains("openapi")) {
            return true;
        }
        if (lower.startsWith("/error")) {
            return true;
        }
        return false;
    }

    static boolean isAppUri(String uri) {
        return !isNoiseUri(uri);
    }
}
