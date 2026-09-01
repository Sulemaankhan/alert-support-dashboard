package com.support.alert.health;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Per application+environment scrape session (collectors, alerts, SSE, latest views). */
final class TargetApmSession {

    final HealthTarget target;
    final RemoteApmCollector remoteCollector;
    final ApmAlertTracker alertTracker = new ApmAlertTracker();
    final List<SseEmitter> apmEmitters = new CopyOnWriteArrayList<>();
    final List<SseEmitter> metricsEmitters = new CopyOnWriteArrayList<>();
    final List<SseEmitter> alertEmitters = new CopyOnWriteArrayList<>();
    final AtomicReference<ApmSnapshot> latestSnapshot = new AtomicReference<>();
    final AtomicReference<ApmMetricsView> latestMetrics = new AtomicReference<>();
    final AtomicReference<ApmAlertsView> latestAlerts = new AtomicReference<>();
    final AtomicReference<Set<String>> knownAlertKeys = new AtomicReference<>(Set.of());
    final LocalTargetMetrics localMetrics = new LocalTargetMetrics();

    TargetApmSession(HealthTarget target, RemoteApmCollector remoteCollector) {
        this.target = target;
        this.remoteCollector = remoteCollector;
    }

    String key() {
        return target.key();
    }

    boolean hasListeners() {
        return !apmEmitters.isEmpty() || !metricsEmitters.isEmpty() || !alertEmitters.isEmpty();
    }
}
