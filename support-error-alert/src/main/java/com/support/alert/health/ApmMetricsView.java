package com.support.alert.health;

import java.time.Instant;
import java.util.List;

/** Lightweight realtime metrics payload for SSE / polling. */
public record ApmMetricsView(
        Instant timestamp,
        String status,
        String serviceName,
        String targetUrl,
        String source,
        long uptimeMs,
        ApmSnapshot.LoadStats load,
        ApmSnapshot.MemoryStats heap,
        ApmSnapshot.MemoryStats nonHeap,
        ApmSnapshot.GcStats gc,
        ApmSnapshot.ThreadStats threads,
        ApmSnapshot.RequestStats requests,
        ApmSnapshot.LatencyStats latency,
        ApmSnapshot.ApdexStats apdex,
        List<ApmSnapshot.TransactionStats> transactions,
        List<ApmSnapshot.MetricSample> recentSamples,
        ApmSnapshot.DatabaseStats database,
        List<ApmSnapshot.ExternalServiceStats> externalServices,
        ApmSnapshot.ServiceMapStats serviceMap,
        String applicationId,
        String applicationName,
        String environment,
        String environmentLabel
) {
    public static ApmMetricsView from(ApmSnapshot snapshot) {
        return new ApmMetricsView(
                snapshot.timestamp(),
                snapshot.status(),
                snapshot.serviceName(),
                snapshot.targetUrl(),
                snapshot.source(),
                snapshot.uptimeMs(),
                snapshot.load(),
                snapshot.heap(),
                snapshot.nonHeap(),
                snapshot.gc(),
                snapshot.threads(),
                snapshot.requests(),
                snapshot.latency(),
                snapshot.apdex(),
                snapshot.transactions(),
                snapshot.recentSamples(),
                snapshot.database(),
                snapshot.externalServices(),
                snapshot.serviceMap(),
                snapshot.applicationId(),
                snapshot.applicationName(),
                snapshot.environment(),
                snapshot.environmentLabel()
        );
    }
}
