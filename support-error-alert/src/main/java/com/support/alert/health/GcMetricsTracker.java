package com.support.alert.health;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tracks GC counter deltas between scrape ticks. */
final class GcMetricsTracker {

    private final Map<String, Long> previousCount = new HashMap<>();
    private final Map<String, Long> previousTimeMs = new HashMap<>();

    synchronized ApmSnapshot.GcStats build(List<ApmSnapshot.GcCollectorStats> collectors) {
        long totalCount = 0;
        long totalTimeMs = 0;
        long countDelta = 0;
        long timeMsDelta = 0;
        List<ApmSnapshot.GcCollectorStats> withDeltas = new ArrayList<>();

        for (ApmSnapshot.GcCollectorStats collector : collectors) {
            long count = Math.max(0, collector.collectionCount());
            long timeMs = Math.max(0, collector.collectionTimeMs());
            long cd = delta(previousCount.get(collector.name()), count);
            long td = delta(previousTimeMs.get(collector.name()), timeMs);
            previousCount.put(collector.name(), count);
            previousTimeMs.put(collector.name(), timeMs);

            withDeltas.add(new ApmSnapshot.GcCollectorStats(
                    collector.name(),
                    count,
                    timeMs,
                    cd,
                    td
            ));
            totalCount += count;
            totalTimeMs += timeMs;
            countDelta += cd;
            timeMsDelta += td;
        }

        return new ApmSnapshot.GcStats(totalCount, totalTimeMs, countDelta, timeMsDelta, List.copyOf(withDeltas));
    }

    static List<ApmSnapshot.GcCollectorStats> fromLocalMxBeans() {
        List<ApmSnapshot.GcCollectorStats> collectors = new ArrayList<>();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            String name = gc.getName() != null ? gc.getName() : "unknown";
            long count = gc.getCollectionCount() >= 0 ? gc.getCollectionCount() : 0;
            long timeMs = gc.getCollectionTime() >= 0 ? gc.getCollectionTime() : 0;
            collectors.add(new ApmSnapshot.GcCollectorStats(name, count, timeMs, 0, 0));
        }
        return collectors;
    }

    private static long delta(Long previous, long current) {
        if (previous == null || current < previous) {
            return 0;
        }
        return current - previous;
    }
}
