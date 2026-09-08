package com.support.alert.health;

import java.time.Instant;
import java.util.List;

/** Fleet health for every configured application (n+1 services). */
public record ServiceHealthBoard(
        Instant timestamp,
        String environment,
        int serviceCount,
        int upCount,
        int degradedCount,
        int downCount,
        List<ServiceHealthView> services
) {
    public record ServiceHealthView(
            String applicationId,
            String applicationName,
            String environment,
            String environmentLabel,
            String serviceName,
            String source,
            String targetUrl,
            String status,
            Instant timestamp,
            long uptimeMs,
            double apdex,
            String apdexRating,
            double heapUsedPercent,
            double requestsPerMinute,
            double errorRatePercent,
            double avgLatencyMs,
            String liveness,
            String readiness,
            String healthStatus,
            String databaseStatus,
            int environmentCount
    ) {
    }
}
