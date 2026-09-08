package com.support.alert.health;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Scrapes one environment per configured application for the Services tab. */
@Service
public class ServiceHealthService {

    private final HealthTargetCatalog catalog;
    private final ApmMetricsService apmMetricsService;
    private final Executor scrapeExecutor = Executors.newFixedThreadPool(
            4,
            runnable -> {
                Thread thread = new Thread(runnable, "health-services-scrape");
                thread.setDaemon(true);
                return thread;
            }
    );

    public ServiceHealthService(HealthTargetCatalog catalog, ApmMetricsService apmMetricsService) {
        this.catalog = catalog;
        this.apmMetricsService = apmMetricsService;
    }

    public ServiceHealthBoard board(String preferredEnv) {
        List<HealthTarget> targets = catalog.primaryTargets(preferredEnv);
        List<CompletableFuture<ServiceHealthBoard.ServiceHealthView>> futures = targets.stream()
                .map(target -> CompletableFuture.supplyAsync(() -> scrape(target), scrapeExecutor))
                .toList();
        List<ServiceHealthBoard.ServiceHealthView> services = new ArrayList<>();
        for (CompletableFuture<ServiceHealthBoard.ServiceHealthView> future : futures) {
            services.add(future.join());
        }
        services.sort(Comparator.comparing(
                ServiceHealthBoard.ServiceHealthView::applicationName,
                String.CASE_INSENSITIVE_ORDER));

        int up = 0;
        int degraded = 0;
        int down = 0;
        for (ServiceHealthBoard.ServiceHealthView row : services) {
            String status = row.status() == null ? "UNKNOWN" : row.status().toUpperCase(Locale.ROOT);
            if ("UP".equals(status)) {
                up++;
            } else if ("DEGRADED".equals(status) || "OUT_OF_SERVICE".equals(status)) {
                degraded++;
            } else {
                down++;
            }
        }
        String env = preferredEnv != null && !preferredEnv.isBlank()
                ? preferredEnv
                : (services.isEmpty() ? "" : services.get(0).environment());
        return new ServiceHealthBoard(
                Instant.now(),
                env,
                services.size(),
                up,
                degraded,
                down,
                List.copyOf(services)
        );
    }

    private ServiceHealthBoard.ServiceHealthView scrape(HealthTarget target) {
        int envCount = (int) catalog.list().stream()
                .filter(item -> item.applicationId().equalsIgnoreCase(target.applicationId()))
                .count();
        try {
            ApmSnapshot snapshot = apmMetricsService.snapshot(target, false);
            return fromSnapshot(target, snapshot, envCount);
        } catch (Exception ex) {
            return down(target, envCount);
        }
    }

    private static ServiceHealthBoard.ServiceHealthView fromSnapshot(
            HealthTarget target,
            ApmSnapshot snapshot,
            int envCount) {
        double apdex = snapshot.apdex() != null ? snapshot.apdex().score() : 0;
        String rating = snapshot.apdex() != null ? snapshot.apdex().rating() : "—";
        double heap = snapshot.heap() != null ? snapshot.heap().usedPercent() : 0;
        double rpm = snapshot.requests() != null ? snapshot.requests().requestsPerMinute() : 0;
        double errors = snapshot.requests() != null ? snapshot.requests().errorRatePercent() : 0;
        double latency = snapshot.latency() != null
                ? snapshot.latency().avgMs()
                : (snapshot.requests() != null ? snapshot.requests().avgResponseTimeMs() : 0);
        String liveness = snapshot.probes() != null ? snapshot.probes().liveness() : "UNKNOWN";
        String readiness = snapshot.probes() != null ? snapshot.probes().readiness() : "UNKNOWN";
        String healthStatus = snapshot.health() != null ? snapshot.health().status() : "UNKNOWN";
        String dbStatus = snapshot.database() != null ? snapshot.database().status() : "UNKNOWN";
        return new ServiceHealthBoard.ServiceHealthView(
                target.applicationId(),
                target.applicationName(),
                target.environmentId(),
                target.environmentLabel(),
                snapshot.serviceName() != null ? snapshot.serviceName() : target.resolvedServiceName(""),
                snapshot.source() != null ? snapshot.source() : (target.isRemote() ? "remote" : "local"),
                snapshot.targetUrl() != null ? snapshot.targetUrl() : target.normalizedActuatorBaseUrl(),
                snapshot.status() != null ? snapshot.status() : "UNKNOWN",
                snapshot.timestamp() != null ? snapshot.timestamp() : Instant.now(),
                snapshot.uptimeMs(),
                apdex,
                rating,
                heap,
                rpm,
                errors,
                latency,
                liveness,
                readiness,
                healthStatus,
                dbStatus,
                envCount
        );
    }

    private static ServiceHealthBoard.ServiceHealthView down(HealthTarget target, int envCount) {
        return new ServiceHealthBoard.ServiceHealthView(
                target.applicationId(),
                target.applicationName(),
                target.environmentId(),
                target.environmentLabel(),
                target.resolvedServiceName(""),
                target.isRemote() ? "remote" : "local",
                target.isRemote() ? target.normalizedActuatorBaseUrl() : "local",
                "DOWN",
                Instant.now(),
                0,
                0,
                "Unacceptable",
                0,
                0,
                0,
                0,
                "DOWN",
                "DOWN",
                "DOWN",
                "UNKNOWN",
                envCount
        );
    }
}
