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
        DatabaseStats database,
        List<ExternalServiceStats> externalServices,
        ServiceMapStats serviceMap,
        String targetUrl,
        String source,
        String applicationId,
        String applicationName,
        String environment,
        String environmentLabel
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

    public record GcStats(
            long collectionCount,
            long collectionTimeMs,
            long collectionCountDelta,
            long collectionTimeMsDelta,
            List<GcCollectorStats> collectors
    ) {
    }

    public record GcCollectorStats(
            String name,
            long collectionCount,
            long collectionTimeMs,
            long collectionCountDelta,
            long collectionTimeMsDelta
    ) {
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
            double nonHeapUsedPercent,
            double requestsPerMinute,
            double errorRatePercent,
            double avgLatencyMs,
            double apdex,
            long gcCollectionCount,
            long gcCollectionTimeMs,
            long gcCollectionCountDelta,
            long gcCollectionTimeMsDelta,
            double dbUsagePercent,
            double externalErrorRatePercent
    ) {
    }

    public record DatabasePoolStats(
            String name,
            String vendor,
            int active,
            int idle,
            int pending,
            int min,
            int max,
            long timeouts,
            double usageAvgMs,
            double acquireAvgMs,
            double usagePercent
    ) {
    }

    public record DatabaseQueryStats(
            String repository,
            String method,
            long count,
            long errorCount,
            double errorRatePercent,
            double avgMs,
            double maxMs
    ) {
    }

    public record DatabaseStats(
            String status,
            String product,
            String validationQuery,
            List<DatabasePoolStats> pools,
            List<DatabaseQueryStats> queries,
            int active,
            int idle,
            int pending,
            int max,
            long timeouts
    ) {
        public static DatabaseStats empty() {
            return new DatabaseStats("UNKNOWN", "", "", List.of(), List.of(), 0, 0, 0, 0, 0);
        }

        public boolean hasSignal() {
            return (pools != null && !pools.isEmpty())
                    || (queries != null && !queries.isEmpty())
                    || (product != null && !product.isBlank())
                    || (status != null && !status.isBlank() && !"UNKNOWN".equalsIgnoreCase(status));
        }
    }

    public record ExternalServiceStats(
            String name,
            String kind,
            String target,
            String uri,
            String method,
            long count,
            long errorCount,
            double errorRatePercent,
            double avgMs,
            double maxMs,
            String healthStatus
    ) {
    }

    public record ServiceMapNode(
            String id,
            String name,
            String kind,
            String status,
            double avgMs,
            long calls,
            double errorRatePercent,
            String detail
    ) {
    }

    public record ServiceMapEdge(
            String from,
            String to,
            long calls,
            double avgMs,
            double errorRatePercent,
            String status
    ) {
    }

    public record ServiceMapStats(
            List<ServiceMapNode> nodes,
            List<ServiceMapEdge> edges
    ) {
        public static ServiceMapStats empty() {
            return new ServiceMapStats(List.of(), List.of());
        }
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
                database,
                externalServices,
                serviceMap,
                targetUrl,
                source,
                applicationId,
                applicationName,
                environment,
                environmentLabel
        );
    }
}
