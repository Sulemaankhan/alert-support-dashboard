package com.support.alert.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Shared realtime broadcaster for APM metrics and alerts.
 * One scrape loop fans out to all SSE subscribers.
 */
@Component
public class ApmRealtimeHub {

    private static final Logger log = LoggerFactory.getLogger(ApmRealtimeHub.class);
    private static final long SSE_TIMEOUT_MS = 0L;

    private final ApmMetricsService apmMetricsService;
    private final ApmAlertTracker alertTracker;
    private final ApmAlertMailService alertMailService;
    private final HealthCheckProperties healthCheckProperties;

    private final List<SseEmitter> apmEmitters = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> metricsEmitters = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> alertEmitters = new CopyOnWriteArrayList<>();

    private final AtomicReference<ApmSnapshot> latestSnapshot = new AtomicReference<>();
    private final AtomicReference<ApmMetricsView> latestMetrics = new AtomicReference<>();
    private final AtomicReference<ApmAlertsView> latestAlerts = new AtomicReference<>(emptyAlerts());
    private final AtomicReference<Set<String>> knownAlertKeys = new AtomicReference<>(Set.of());

    public ApmRealtimeHub(
            ApmMetricsService apmMetricsService,
            ApmAlertTracker alertTracker,
            ApmAlertMailService alertMailService,
            HealthCheckProperties healthCheckProperties) {
        this.apmMetricsService = apmMetricsService;
        this.alertTracker = alertTracker;
        this.alertMailService = alertMailService;
        this.healthCheckProperties = healthCheckProperties;
    }

    public SseEmitter subscribeApm() {
        return register(apmEmitters);
    }

    public SseEmitter subscribeMetrics() {
        SseEmitter emitter = register(metricsEmitters);
        ApmMetricsView metrics = latestMetrics.get();
        if (metrics != null) {
            sendQuiet(emitter, "metrics", metrics);
        }
        return emitter;
    }

    public SseEmitter subscribeAlerts() {
        SseEmitter emitter = register(alertEmitters);
        // Always push a fresh view (never the blank bootstrap placeholder).
        sendQuiet(emitter, "alerts", currentAlerts());
        return emitter;
    }

    public ApmMetricsView currentMetrics() {
        ApmMetricsView cached = latestMetrics.get();
        if (cached != null && !isStale(cached.timestamp())) {
            return cached;
        }
        ApmSnapshot snapshot = refreshSnapshot(false);
        return ApmMetricsView.from(snapshot);
    }

    public ApmAlertsView currentAlerts() {
        ApmAlertsView cached = latestAlerts.get();
        if (cached != null
                && cached.timestamp() != null
                && !isStale(cached.timestamp())
                && cached.serviceName() != null
                && !cached.serviceName().isBlank()) {
            return cached;
        }
        ApmSnapshot snapshot = currentSnapshot();
        ApmAlertsView view = buildAlertsView(snapshot, List.of());
        latestAlerts.set(view);
        return view;
    }

    public ApmSnapshot currentSnapshot() {
        ApmSnapshot cached = latestSnapshot.get();
        if (cached != null && !isStale(cached.timestamp())) {
            return cached;
        }
        return refreshSnapshot(true);
    }

    private ApmSnapshot refreshSnapshot(boolean includeStacks) {
        ApmSnapshot snapshot = apmMetricsService.snapshot(includeStacks);
        latestSnapshot.set(snapshot);
        latestMetrics.set(ApmMetricsView.from(snapshot));
        latestAlerts.set(buildAlertsView(snapshot, List.of()));
        return snapshot;
    }

    private ApmAlertsView buildAlertsView(ApmSnapshot snapshot, List<ApmSnapshot.AlertEvent> newest) {
        List<ApmSnapshot.AlertEvent> history = snapshot.alerts() != null
                ? snapshot.alerts()
                : alertTracker.recent();
        List<ApmSnapshot.AlertEvent> active = alertTracker.activeConditions(snapshot);
        return new ApmAlertsView(
                Instant.now(),
                snapshot.serviceName() != null ? snapshot.serviceName() : "",
                snapshot.status() != null ? snapshot.status() : "UNKNOWN",
                active.size(),
                active,
                history,
                newest != null ? newest : List.of()
        );
    }

    private static boolean isStale(Instant timestamp) {
        if (timestamp == null) {
            return true;
        }
        return Instant.now().toEpochMilli() - timestamp.toEpochMilli() > 2_000L;
    }

    @Scheduled(fixedDelayString = "${support.healthcheck.realtime-interval-ms:1000}")
    public void tick() {
        boolean hasListeners = !apmEmitters.isEmpty() || !metricsEmitters.isEmpty() || !alertEmitters.isEmpty();
        if (!hasListeners) {
            // Email poll runs on its own schedule when the UI is closed.
            return;
        }
        scrapeAndFanOut(true);
    }

    /**
     * Evaluates APM + emails alert metrics even when no UI SSE clients are connected.
     */
    @Scheduled(fixedDelayString = "${support.healthcheck.alert-email-poll-interval-ms:15000}")
    public void emailWatchTick() {
        if (!healthCheckProperties.isAlertEmailConfigured()) {
            return;
        }
        boolean hasListeners = !apmEmitters.isEmpty() || !metricsEmitters.isEmpty() || !alertEmitters.isEmpty();
        if (hasListeners) {
            // Live tick already emails on the same scrape.
            return;
        }
        scrapeAndFanOut(false);
    }

    private void scrapeAndFanOut(boolean includeStacksIfNeeded) {
        try {
            boolean needStacks = includeStacksIfNeeded && !apmEmitters.isEmpty();
            ApmSnapshot snapshot = apmMetricsService.snapshot(needStacks);
            latestSnapshot.set(snapshot);

            ApmMetricsView metrics = ApmMetricsView.from(snapshot);
            latestMetrics.set(metrics);

            List<ApmSnapshot.AlertEvent> alerts = snapshot.alerts() != null ? snapshot.alerts() : alertTracker.recent();
            List<ApmSnapshot.AlertEvent> newest = detectNewAlerts(alerts);
            ApmAlertsView alertsView = buildAlertsView(snapshot, newest);
            latestAlerts.set(alertsView);

            alertMailService.notifyActiveAlerts(snapshot, alertsView.active());

            broadcast(apmEmitters, "apm", snapshot);
            broadcast(metricsEmitters, "metrics", metrics);
            broadcast(alertEmitters, "alerts", alertsView);

            if (!newest.isEmpty()) {
                broadcast(alertEmitters, "alert", newest.get(0));
                broadcast(apmEmitters, "alert", newest.get(0));
            }
        } catch (Exception ex) {
            log.debug("APM realtime tick failed: {}", ex.toString());
        }
    }

    private List<ApmSnapshot.AlertEvent> detectNewAlerts(List<ApmSnapshot.AlertEvent> alerts) {
        Set<String> keys = alerts.stream().map(this::keyOf).collect(Collectors.toSet());
        Set<String> previous = knownAlertKeys.getAndSet(keys);
        if (previous == null || previous.isEmpty()) {
            return List.of();
        }
        List<ApmSnapshot.AlertEvent> newest = new ArrayList<>();
        for (ApmSnapshot.AlertEvent alert : alerts) {
            if (!previous.contains(keyOf(alert))) {
                newest.add(alert);
            }
        }
        return newest;
    }

    private String keyOf(ApmSnapshot.AlertEvent alert) {
        return alert.code() + "|" + (alert.timestamp() != null ? alert.timestamp().toString() : "") + "|" + alert.message();
    }

    private SseEmitter register(List<SseEmitter> bucket) {
        boolean wasIdle = apmEmitters.isEmpty() && metricsEmitters.isEmpty() && alertEmitters.isEmpty();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        bucket.add(emitter);
        Runnable remove = () -> bucket.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            remove.run();
            emitter.complete();
        });
        emitter.onError(ex -> remove.run());
        if (wasIdle) {
            // First subscriber: scrape immediately instead of waiting for the next schedule tick.
            Thread starter = new Thread(this::tick, "apm-realtime-kick");
            starter.setDaemon(true);
            starter.start();
        }
        return emitter;
    }

    private void broadcast(List<SseEmitter> bucket, String eventName, Object payload) {
        for (SseEmitter emitter : bucket) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException ex) {
                bucket.remove(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    /* closed */
                }
            }
        }
    }

    private void sendQuiet(SseEmitter emitter, String eventName, Object payload) {
        if (payload == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload).reconnectTime(1000L));
        } catch (IOException | IllegalStateException ignored) {
            /* subscriber will retry */
        }
    }

    private static ApmAlertsView emptyAlerts() {
        return new ApmAlertsView(Instant.now(), "", "UNKNOWN", 0, List.of(), List.of(), List.of());
    }

    /** For Spring MVC content type checks. */
    public static MediaType eventStream() {
        return MediaType.TEXT_EVENT_STREAM;
    }
}
