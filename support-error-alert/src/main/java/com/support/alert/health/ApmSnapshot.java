package com.support.alert.health;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** New Relic–style APM snapshot (local JVM or remote Actuator target). */
public record ApmSnapshot(
        String status,
        Instant timestamp,
        String serviceName,
        long uptimeMs,
        LoadStats load,
        MemoryStats heap,
        MemoryStats nonHeap,
        GcStats gc,
        ThreadStats threads,
        HealthStats health,
        ProbeStats probes,
        RequestStats requests,
        LatencyStats latency,
        ApdexStats apdex,
        List<TransactionStats> transactions,
        List<AlertEvent> alerts,
        List<ThreadStack> topStacks,
        List<MetricSample> recentSamples,
        String targetUrl,
        String source
) {
    public record LoadStats(
            double processCpuLoad,
            double systemCpuLoad,
            double systemLoadAverage,
            int availableProcessors
    ) {
    }

    public record MemoryStats(long usedBytes, long committedBytes, long maxBytes, double usedPercent) {
    }

    public record GcStats(long collectionCount, long collectionTimeMs) {
    }

    public record ThreadStats(int live, int peak, int daemon, int runnable, int blocked, int waiting) {
    }

    public record HealthStats(String status, Map<String, String> components) {
    }

    public record ProbeStats(String liveness, String readiness) {
    }

    public record RequestStats(
            long totalRequests,
            long errorRequests,
            long requestsDelta,
            long errorsDelta,
            double requestsPerMinute,
            double errorRatePercent,
            double avgResponseTimeMs
    ) {
    }

    public record LatencyStats(double avgMs, double maxMs, double apdexThresholdMs) {
    }

    public record ApdexStats(double score, String rating, double thresholdMs) {
    }

    public record TransactionStats(
            String uri,
            String method,
            long count,
            long errorCount,
            double errorRatePercent,
            double avgMs,
            double maxMs,
            double apdex
    ) {
    }

    public record AlertEvent(Instant timestamp, String severity, String code, String message) {
    }

    public record ThreadStack(String name, String state, long cpuTimeMs, List<String> frames) {
    }

    public record MetricSample(
            Instant timestamp,
            double processCpuLoad,
            double heapUsedPercent,
            double requestsPerMinute,
            double errorRatePercent,
            double avgLatencyMs,
            double apdex
    ) {
    }

    public ApmSnapshot withAlerts(List<AlertEvent> nextAlerts) {
        return new ApmSnapshot(
                status,
                timestamp,
                serviceName,
                uptimeMs,
                load,
                heap,
                nonHeap,
                gc,
                threads,
                health,
                probes,
                requests,
                latency,
                apdex,
                transactions,
                List.copyOf(nextAlerts),
                topStacks,
                recentSamples,
                targetUrl,
                source
        );
    }
}
