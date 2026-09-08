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
import java.util.stream.Collectors;

/**
 * Shared realtime broadcaster for APM metrics and alerts.
 * One scrape loop per selected application+environment fans out to that target's SSE subscribers.
 */
@Component
public class ApmRealtimeHub {

    private static final Logger log = LoggerFactory.getLogger(ApmRealtimeHub.class);
    private static final long SSE_TIMEOUT_MS = 0L;

    private final ApmMetricsService apmMetricsService;
    private final ApmAlertMailService alertMailService;
    private final HealthCheckProperties healthCheckProperties;
    private final HealthTargetCatalog targetCatalog;
    private final TargetApmRegistry targetApmRegistry;

    public ApmRealtimeHub(
            ApmMetricsService apmMetricsService,
            ApmAlertMailService alertMailService,
            HealthCheckProperties healthCheckProperties,
            HealthTargetCatalog targetCatalog,
            TargetApmRegistry targetApmRegistry) {
        this.apmMetricsService = apmMetricsService;
        this.alertMailService = alertMailService;
        this.healthCheckProperties = healthCheckProperties;
        this.targetCatalog = targetCatalog;
        this.targetApmRegistry = targetApmRegistry;
    }

    public SseEmitter subscribeApm(HealthTarget target) {
        TargetApmSession session = targetApmRegistry.session(target);
        return register(session, session.apmEmitters);
    }

    public SseEmitter subscribeMetrics(HealthTarget target) {
        TargetApmSession session = targetApmRegistry.session(target);
        SseEmitter emitter = register(session, session.metricsEmitters);
        ApmMetricsView metrics = session.latestMetrics.get();
        if (metrics != null) {
            sendQuiet(emitter, "metrics", metrics);
        }
        return emitter;
    }

    public SseEmitter subscribeAlerts(HealthTarget target) {
        TargetApmSession session = targetApmRegistry.session(target);
        SseEmitter emitter = register(session, session.alertEmitters);
        sendQuiet(emitter, "alerts", currentAlerts(target));
        return emitter;
    }

    public ApmMetricsView currentMetrics(HealthTarget target) {
        TargetApmSession session = targetApmRegistry.session(target);
        ApmMetricsView cached = session.latestMetrics.get();
        if (cached != null && !isStale(cached.timestamp())) {
            return cached;
        }
        ApmSnapshot snapshot = refreshSnapshot(session, false);
        return ApmMetricsView.from(snapshot);
    }

    public ApmAlertsView currentAlerts(HealthTarget target) {
        TargetApmSession session = targetApmRegistry.session(target);
        ApmAlertsView cached = session.latestAlerts.get();
        if (cached != null
                && cached.timestamp() != null
                && !isStale(cached.timestamp())
                && cached.serviceName() != null
                && !cached.serviceName().isBlank()) {
            return cached;
        }
        ApmSnapshot snapshot = currentSnapshot(target);
        ApmAlertsView view = buildAlertsView(session, snapshot, List.of());
        session.latestAlerts.set(view);
        return view;
    }

    public ApmSnapshot currentSnapshot(HealthTarget target) {
        TargetApmSession session = targetApmRegistry.session(target);
        ApmSnapshot cached = session.latestSnapshot.get();
        if (cached != null && !isStale(cached.timestamp())) {
            return cached;
        }
        return refreshSnapshot(session, true);
    }

    private ApmSnapshot refreshSnapshot(TargetApmSession session, boolean includeStacks) {
        ApmSnapshot snapshot = apmMetricsService.snapshot(session.target, includeStacks);
        session.latestSnapshot.set(snapshot);
        session.latestMetrics.set(ApmMetricsView.from(snapshot));
        session.latestAlerts.set(buildAlertsView(session, snapshot, List.of()));
        return snapshot;
    }

    private ApmAlertsView buildAlertsView(
            TargetApmSession session,
            ApmSnapshot snapshot,
            List<ApmSnapshot.AlertEvent> newest) {
        List<ApmSnapshot.AlertEvent> history = snapshot.alerts() != null
                ? snapshot.alerts()
                : session.alertTracker.recent();
        List<ApmSnapshot.AlertEvent> active = session.alertTracker.activeConditions(snapshot);
        return new ApmAlertsView(
                Instant.now(),
                snapshot.serviceName() != null ? snapshot.serviceName() : "",
                snapshot.status() != null ? snapshot.status() : "UNKNOWN",
                active.size(),
                active,
                history,
                newest != null ? newest : List.of(),
                snapshot.applicationId(),
                snapshot.applicationName(),
                snapshot.environment(),
                snapshot.environmentLabel()
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
        for (TargetApmSession session : targetApmRegistry.all()) {
            if (session.hasListeners()) {
                scrapeAndFanOut(session, true);
            }
        }
    }

    /**
     * Evaluates APM + emails alert metrics even when no UI SSE clients are connected.
     */
    @Scheduled(fixedDelayString = "${support.healthcheck.alert-email-poll-interval-ms:15000}")
    public void emailWatchTick() {
        if (!healthCheckProperties.isAlertEmailConfigured()) {
            return;
        }
        HealthTarget defaultTarget = targetCatalog.defaultTarget();
        TargetApmSession session = targetApmRegistry.session(defaultTarget);
        if (session.hasListeners()) {
            return;
        }
        scrapeAndFanOut(session, false);
    }

    private void scrapeAndFanOut(TargetApmSession session, boolean includeStacksIfNeeded) {
        try {
            boolean needStacks = includeStacksIfNeeded && !session.apmEmitters.isEmpty();
            ApmSnapshot snapshot = apmMetricsService.snapshot(session.target, needStacks);
            session.latestSnapshot.set(snapshot);

            ApmMetricsView metrics = ApmMetricsView.from(snapshot);
            session.latestMetrics.set(metrics);

            List<ApmSnapshot.AlertEvent> alerts = snapshot.alerts() != null
                    ? snapshot.alerts()
                    : session.alertTracker.recent();
            List<ApmSnapshot.AlertEvent> newest = detectNewAlerts(session, alerts);
            ApmAlertsView alertsView = buildAlertsView(session, snapshot, newest);
            session.latestAlerts.set(alertsView);

            alertMailService.notifyActiveAlerts(snapshot, alertsView.active());

            broadcast(session.apmEmitters, "apm", snapshot);
            broadcast(session.metricsEmitters, "metrics", metrics);
            broadcast(session.alertEmitters, "alerts", alertsView);

            if (!newest.isEmpty()) {
                broadcast(session.alertEmitters, "alert", newest.get(0));
                broadcast(session.apmEmitters, "alert", newest.get(0));
            }
        } catch (Exception ex) {
            log.debug("APM realtime tick failed for {}: {}", session.key(), ex.toString());
        }
    }

    private List<ApmSnapshot.AlertEvent> detectNewAlerts(TargetApmSession session, List<ApmSnapshot.AlertEvent> alerts) {
        Set<String> keys = alerts.stream().map(this::keyOf).collect(Collectors.toSet());
        Set<String> previous = session.knownAlertKeys.getAndSet(keys);
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

    private SseEmitter register(TargetApmSession session, List<SseEmitter> bucket) {
        boolean wasIdle = !session.hasListeners();
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
            Thread starter = new Thread(() -> scrapeAndFanOut(session, true), "apm-realtime-kick-" + session.key());
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

    /** For Spring MVC content type checks. */
    public static MediaType eventStream() {
        return MediaType.TEXT_EVENT_STREAM;
    }
}
