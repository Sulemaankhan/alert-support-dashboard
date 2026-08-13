package com.support.alert.health;

import java.util.List;

final class ApmScoring {

    private ApmScoring() {
    }

    static ApmSnapshot.ApdexStats apdex(double avgMs, double thresholdMs) {
        double score = scoreFromAvg(avgMs, thresholdMs);
        return new ApmSnapshot.ApdexStats(score, rating(score), thresholdMs);
    }

    static double scoreFromAvg(double avgMs, double thresholdMs) {
        if (avgMs < 0 || thresholdMs <= 0) {
            return 1.0;
        }
        if (avgMs <= thresholdMs) {
            return 1.0;
        }
        if (avgMs <= 4 * thresholdMs) {
            return 0.5;
        }
        return 0.0;
    }

    static ApmSnapshot.ApdexStats weightedApdex(List<ApmSnapshot.TransactionStats> transactions, double thresholdMs) {
        if (transactions == null || transactions.isEmpty()) {
            return apdex(0, thresholdMs);
        }
        long total = 0;
        double weighted = 0;
        for (ApmSnapshot.TransactionStats tx : transactions) {
            long count = Math.max(0, tx.count());
            total += count;
            weighted += count * tx.apdex();
        }
        if (total <= 0) {
            return apdex(0, thresholdMs);
        }
        double score = round2(weighted / total);
        return new ApmSnapshot.ApdexStats(score, rating(score), thresholdMs);
    }

    static String rating(double score) {
        if (score >= 0.94) {
            return "Excellent";
        }
        if (score >= 0.85) {
            return "Good";
        }
        if (score >= 0.7) {
            return "Fair";
        }
        if (score >= 0.5) {
            return "Poor";
        }
        return "Unacceptable";
    }

    static String deriveStatus(
            ApmSnapshot.MemoryStats heap,
            ApmSnapshot.RequestStats requests,
            ApmSnapshot.HealthStats health,
            ApmSnapshot.ProbeStats probes,
            ApmSnapshot.ApdexStats apdex) {
        if (health != null && "DOWN".equalsIgnoreCase(health.status())) {
            return "DOWN";
        }
        if (probes != null && ("DOWN".equalsIgnoreCase(probes.liveness()) || "DOWN".equalsIgnoreCase(probes.readiness()))) {
            return "DOWN";
        }
        boolean degradedHeap = heap != null && heap.usedPercent() >= 85.0;
        boolean degradedErrors = requests != null && requests.errorRatePercent() >= 5.0;
        boolean degradedApdex = apdex != null && apdex.score() < 0.7;
        boolean outOfService = health != null && "OUT_OF_SERVICE".equalsIgnoreCase(health.status());
        if (degradedHeap || degradedErrors || degradedApdex || outOfService) {
            return "DEGRADED";
        }
        return "UP";
    }

    static double round2(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return Math.round(value * 100.0) / 100.0;
    }
}
