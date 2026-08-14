package com.support.alert.health;

import java.util.ArrayDeque;

/**
 * Throughput estimator: EWMA of per-tick rate + rolling cumulative window.
 * EWMA decays slowly so brief traffic bursts stay visible; the window
 * captures sustained rate over {@code windowMs}.
 */
final class RpmWindow {

    private final long windowMs;
    private final ArrayDeque<long[]> samples = new ArrayDeque<>();
    private double ewma;
    private Long lastTotal;
    private Long lastAtMs;

    RpmWindow(long windowMs) {
        this.windowMs = Math.max(30_000L, windowMs);
    }

    void clear() {
        samples.clear();
        ewma = 0;
        lastTotal = null;
        lastAtMs = null;
    }

    /**
     * @param nowMs       sample time
     * @param totalCount  cumulative app request count
     * @return requests-per-minute
     */
    double observe(long nowMs, long totalCount) {
        if (lastTotal != null && totalCount < lastTotal) {
            clear();
        }

        long tickDelta = 0;
        double instantRpm = 0;
        if (lastTotal != null && lastAtMs != null) {
            tickDelta = Math.max(0, totalCount - lastTotal);
            double seconds = Math.max(0.2, (nowMs - lastAtMs) / 1000.0);
            instantRpm = (tickDelta / seconds) * 60.0;
        }

        // Fast rise on traffic, slow decay when idle (~half-life ~20s at 1 Hz).
        if (tickDelta > 0) {
            ewma = ewma <= 0 ? instantRpm : (ewma * 0.35 + instantRpm * 0.65);
        } else if (ewma > 0) {
            ewma *= 0.96;
            if (ewma < 0.05) {
                ewma = 0;
            }
        }

        samples.addLast(new long[]{nowMs, totalCount});
        while (samples.size() > 1 && nowMs - samples.peekFirst()[0] > windowMs) {
            samples.removeFirst();
        }

        double windowRpm = 0;
        if (samples.size() >= 2) {
            long[] first = samples.peekFirst();
            long[] last = samples.peekLast();
            long delta = Math.max(0, last[1] - first[1]);
            double minutes = Math.max(1.0 / 60.0, (last[0] - first[0]) / 60_000.0);
            windowRpm = delta / minutes;
        }

        lastTotal = totalCount;
        lastAtMs = nowMs;

        double rpm = Math.max(ewma, windowRpm);
        if (Double.isNaN(rpm) || Double.isInfinite(rpm) || rpm < 0.05) {
            return 0;
        }
        return Math.round(rpm * 100.0) / 100.0;
    }
}
