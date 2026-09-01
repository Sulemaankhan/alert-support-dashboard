package com.support.alert.health;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

/** Keeps a short in-memory APM alert timeline (threshold crossings / status changes). */
public class ApmAlertTracker {

    private static final int MAX_ALERTS = 40;

    private final Deque<ApmSnapshot.AlertEvent> events = new ConcurrentLinkedDeque<>();
    private final AtomicReference<String> lastStatus = new AtomicReference<>("UNKNOWN");
    private final AtomicReference<Boolean> heapAlertOpen = new AtomicReference<>(false);
    private final AtomicReference<Boolean> errorAlertOpen = new AtomicReference<>(false);
    private final AtomicReference<Boolean> apdexAlertOpen = new AtomicReference<>(false);
    private final AtomicReference<Boolean> statusAlertOpen = new AtomicReference<>(false);

    public synchronized List<ApmSnapshot.AlertEvent> evaluate(ApmSnapshot snapshot) {
        Instant now = snapshot.timestamp() != null ? snapshot.timestamp() : Instant.now();
        String status = snapshot.status() != null ? snapshot.status() : "UNKNOWN";
        String previous = lastStatus.getAndSet(status);

        boolean unhealthy = "DOWN".equalsIgnoreCase(status) || "DEGRADED".equalsIgnoreCase(status);
        if (unhealthy && !Boolean.TRUE.equals(statusAlertOpen.getAndSet(true))) {
            String severity = "DOWN".equalsIgnoreCase(status) ? "critical" : "warning";
            push(now, severity, "STATUS_" + status.toUpperCase(), "Service status is " + status);
        } else if (!unhealthy) {
            if (Boolean.TRUE.equals(statusAlertOpen.getAndSet(false))
                    && !"UNKNOWN".equalsIgnoreCase(previous)
                    && !status.equalsIgnoreCase(previous)) {
                push(now, "info", "STATUS_RECOVERED", "Service status recovered " + previous + " → " + status);
            }
        } else if (!status.equalsIgnoreCase(previous) && !"UNKNOWN".equalsIgnoreCase(previous)) {
            String severity = "DOWN".equalsIgnoreCase(status) ? "critical" : "warning";
            push(now, severity, "STATUS_CHANGE", "Service status changed " + previous + " → " + status);
        }

        double heap = snapshot.heap() != null ? snapshot.heap().usedPercent() : 0;
        boolean heapHigh = heap >= 85.0;
        if (heapHigh && !Boolean.TRUE.equals(heapAlertOpen.getAndSet(true))) {
            push(now, "warning", "HEAP_HIGH", "Heap usage high at " + round1(heap) + "%");
        } else if (!heapHigh) {
            heapAlertOpen.set(false);
        }

        double errorRate = snapshot.requests() != null ? snapshot.requests().errorRatePercent() : 0;
        boolean errorsHigh = errorRate >= 5.0;
        if (errorsHigh && !Boolean.TRUE.equals(errorAlertOpen.getAndSet(true))) {
            push(now, "critical", "ERROR_RATE_HIGH", "Error rate elevated at " + round1(errorRate) + "%");
        } else if (!errorsHigh) {
            errorAlertOpen.set(false);
        }

        double apdex = snapshot.apdex() != null ? snapshot.apdex().score() : 1.0;
        boolean apdexLow = apdex < 0.7;
        if (apdexLow && !Boolean.TRUE.equals(apdexAlertOpen.getAndSet(true))) {
            String rating = snapshot.apdex() != null ? snapshot.apdex().rating() : "Poor";
            push(now, "warning", "APDEX_LOW", "Apdex dropped to " + round2(apdex) + " (" + rating + ")");
        } else if (!apdexLow) {
            apdexAlertOpen.set(false);
        }

        if ("DOWN".equalsIgnoreCase(status) && snapshot.health() != null && snapshot.health().components() != null) {
            String remote = snapshot.health().components().get("remote");
            if (remote != null && !remote.isBlank() && !status.equalsIgnoreCase(previous)) {
                push(now, "critical", "TARGET_UNREACHABLE", remote);
            }
        }

        return recent();
    }

    /**
     * Currently firing conditions (not historical timeline). Used for Active alerts count.
     */
    public List<ApmSnapshot.AlertEvent> activeConditions(ApmSnapshot snapshot) {
        Instant now = snapshot.timestamp() != null ? snapshot.timestamp() : Instant.now();
        List<ApmSnapshot.AlertEvent> active = new ArrayList<>();

        String status = snapshot.status() != null ? snapshot.status() : "UNKNOWN";
        if ("DOWN".equalsIgnoreCase(status)) {
            active.add(new ApmSnapshot.AlertEvent(now, "critical", "STATUS_DOWN", "Service status is DOWN"));
        } else if ("DEGRADED".equalsIgnoreCase(status)) {
            active.add(new ApmSnapshot.AlertEvent(now, "warning", "STATUS_DEGRADED", "Service status is DEGRADED"));
        }

        if (snapshot.probes() != null) {
            if ("DOWN".equalsIgnoreCase(snapshot.probes().liveness())) {
                active.add(new ApmSnapshot.AlertEvent(now, "critical", "LIVENESS_DOWN", "Liveness probe is DOWN"));
            }
            if ("DOWN".equalsIgnoreCase(snapshot.probes().readiness())) {
                active.add(new ApmSnapshot.AlertEvent(now, "critical", "READINESS_DOWN", "Readiness probe is DOWN"));
            }
        }

        double heap = snapshot.heap() != null ? snapshot.heap().usedPercent() : 0;
        if (heap >= 85.0) {
            active.add(new ApmSnapshot.AlertEvent(
                    now, "warning", "HEAP_HIGH", "Heap usage high at " + round1(heap) + "%"));
        }

        double errorRate = snapshot.requests() != null ? snapshot.requests().errorRatePercent() : 0;
        if (errorRate >= 5.0) {
            active.add(new ApmSnapshot.AlertEvent(
                    now, "critical", "ERROR_RATE_HIGH", "Error rate elevated at " + round1(errorRate) + "%"));
        }

        double apdex = snapshot.apdex() != null ? snapshot.apdex().score() : 1.0;
        if (apdex < 0.7) {
            String rating = snapshot.apdex() != null ? snapshot.apdex().rating() : "Poor";
            active.add(new ApmSnapshot.AlertEvent(
                    now, "warning", "APDEX_LOW", "Apdex dropped to " + round2(apdex) + " (" + rating + ")"));
        }

        return List.copyOf(active);
    }

    public int countActive(ApmSnapshot snapshot) {
        return activeConditions(snapshot).size();
    }

    public List<ApmSnapshot.AlertEvent> recent() {
        return List.copyOf(new ArrayList<>(events));
    }

    private void push(Instant at, String severity, String code, String message) {
        events.addFirst(new ApmSnapshot.AlertEvent(at, severity, code, message));
        while (events.size() > MAX_ALERTS) {
            events.removeLast();
        }
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
