package com.support.alert.health;

import java.time.Instant;
import java.util.List;

/** On-demand heap breakdown: memory pools and per-class shallow usage. */
public record HeapAnalysisView(
        Instant timestamp,
        String serviceName,
        String applicationId,
        String applicationName,
        String environment,
        String environmentLabel,
        String source,
        String targetUrl,
        boolean histogramAvailable,
        String histogramNote,
        List<MemoryPoolUsage> pools,
        List<ClassMemoryUsage> classes,
        long totalShallowBytes,
        int classCount
) {
    public record MemoryPoolUsage(
            String id,
            String area,
            long usedBytes,
            long committedBytes,
            long maxBytes,
            double usedPercent
    ) {
    }

    public record ClassMemoryUsage(
            int rank,
            String className,
            long instanceCount,
            long shallowBytes,
            double percentOfTotal
    ) {
    }
}
