package com.support.alert.health;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Per-target scrape state for local JVM metrics (requests RPM window, samples, GC deltas). */
final class LocalTargetMetrics {

    static final int MAX_SAMPLES = 120;

    final List<ApmSnapshot.MetricSample> recentSamples = new ArrayList<>();
    final GcMetricsTracker gcMetricsTracker = new GcMetricsTracker();
    final AtomicReference<RequestCounters> previousCounters = new AtomicReference<>(RequestCounters.ZERO);
    final AtomicReference<Instant> previousSampleAt = new AtomicReference<>(null);
    final RpmWindow rpmWindow = new RpmWindow(300_000L);

    void addSample(ApmSnapshot.MetricSample sample) {
        recentSamples.add(sample);
        while (recentSamples.size() > MAX_SAMPLES) {
            recentSamples.remove(0);
        }
    }

    record RequestCounters(long total, long errors, double totalMs) {
        static final RequestCounters ZERO = new RequestCounters(0, 0, 0);
    }
}
