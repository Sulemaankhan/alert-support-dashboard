package com.support.alert.health;

import com.sun.management.OperatingSystemMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.SystemHealth;
import org.springframework.stereotype.Service;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ApmMetricsService {

    private static final int MAX_SAMPLES = 120;
    private static final int TOP_STACK_THREADS = 8;
    private static final int STACK_FRAMES = 12;

    private final String localServiceName;
    private final HealthCheckProperties healthCheckProperties;
    private final RemoteApmCollector remoteApmCollector;
    private final ApmAlertTracker alertTracker;
    private final MeterRegistry meterRegistry;
    private final HealthEndpoint healthEndpoint;
    private final List<ApmSnapshot.MetricSample> recentSamples = new ArrayList<>();
    private final AtomicReference<RequestCounters> previousCounters = new AtomicReference<>(RequestCounters.ZERO);
    private final AtomicReference<Instant> previousSampleAt = new AtomicReference<>(null);
    private final RpmWindow rpmWindow = new RpmWindow(300_000L);

    public ApmMetricsService(
            @Value("${spring.application.name:support-error-alert}") String localServiceName,
            HealthCheckProperties healthCheckProperties,
            RemoteApmCollector remoteApmCollector,
            ApmAlertTracker alertTracker,
            MeterRegistry meterRegistry,
            HealthEndpoint healthEndpoint) {
        this.localServiceName = localServiceName;
        this.healthCheckProperties = healthCheckProperties;
        this.remoteApmCollector = remoteApmCollector;
        this.alertTracker = alertTracker;
        this.meterRegistry = meterRegistry;
        this.healthEndpoint = healthEndpoint;
    }

    public synchronized ApmSnapshot snapshot() {
        return snapshot(true);
    }

    public synchronized ApmSnapshot snapshot(boolean includeStacks) {
        ApmSnapshot base = healthCheckProperties.isRemoteConfigured()
                ? remoteApmCollector.collect(includeStacks)
                : localSnapshot(includeStacks);
        return base.withAlerts(alertTracker.evaluate(base));
    }

    public synchronized List<ApmSnapshot.ThreadStack> fullStackDump() {
        if (healthCheckProperties.isRemoteConfigured()) {
            return remoteApmCollector.fullStackDump();
        }
        return readTopStacks(ManagementFactory.getThreadMXBean(), 40, 40);
    }

    private ApmSnapshot localSnapshot(boolean includeStacks) {
        double apdexThreshold = healthCheckProperties.getApdexThresholdMs() > 0
                ? healthCheckProperties.getApdexThresholdMs()
                : 500;
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();

        ApmSnapshot.MemoryStats heap = toMemoryStats(memory.getHeapMemoryUsage());
        ApmSnapshot.MemoryStats nonHeap = toMemoryStats(memory.getNonHeapMemoryUsage());
        ApmSnapshot.LoadStats load = readLoad();
        ApmSnapshot.GcStats gc = readGc();
        ApmSnapshot.ThreadStats threadStats = readThreadStats(threads);
        ApmSnapshot.HealthStats health = readHealth();
        ApmSnapshot.ProbeStats probes = readProbes();
        RequestBundle requestBundle = readRequests();
        List<ApmSnapshot.TransactionStats> transactions = readTransactions(apdexThreshold);
        ApmSnapshot.ApdexStats apdex = transactions.isEmpty()
                ? ApmScoring.apdex(requestBundle.requests().avgResponseTimeMs(), apdexThreshold)
                : ApmScoring.weightedApdex(transactions, apdexThreshold);
        ApmSnapshot.LatencyStats latency = new ApmSnapshot.LatencyStats(
                requestBundle.requests().avgResponseTimeMs(),
                requestBundle.maxMs(),
                apdexThreshold
        );
        List<ApmSnapshot.ThreadStack> topStacks = includeStacks ? readTopStacks(threads) : List.of();

        Instant now = Instant.now();
        recentSamples.add(new ApmSnapshot.MetricSample(
                now,
                load.processCpuLoad(),
                heap.usedPercent(),
                requestBundle.requests().requestsPerMinute(),
                requestBundle.requests().errorRatePercent(),
                requestBundle.requests().avgResponseTimeMs(),
                apdex.score()
        ));
        while (recentSamples.size() > MAX_SAMPLES) {
            recentSamples.remove(0);
        }

        String status = ApmScoring.deriveStatus(heap, requestBundle.requests(), health, probes, apdex);

        return new ApmSnapshot(
                status,
                now,
                localServiceName,
                runtime.getUptime(),
                load,
                heap,
                nonHeap,
                gc,
                threadStats,
                health,
                probes,
                requestBundle.requests(),
                latency,
                apdex,
                transactions,
                List.of(),
                topStacks,
                List.copyOf(recentSamples),
                "local",
                "local"
        );
    }

    private List<ApmSnapshot.TransactionStats> readTransactions(double apdexThreshold) {
        Collection<Timer> timers = meterRegistry.find("http.server.requests").timers();
        Map<String, Agg> byKey = new HashMap<>();
        for (Timer timer : timers) {
            String uri = timer.getId().getTag("uri");
            if (ApmUriFilters.isNoiseUri(uri)) {
                continue;
            }
            String methodTag = timer.getId().getTag("method");
            String method = (methodTag == null || methodTag.isBlank()) ? "*" : methodTag;
            String key = method + " " + uri;
            Agg agg = byKey.computeIfAbsent(key, ignored -> new Agg(uri, method));
            long count = timer.count();
            agg.count += count;
            agg.totalMs += timer.totalTime(TimeUnit.MILLISECONDS);
            agg.maxMs = Math.max(agg.maxMs, timer.max(TimeUnit.MILLISECONDS));
            String status = timer.getId().getTag("status");
            String outcome = timer.getId().getTag("outcome");
            if (isHttpError(status, outcome)) {
                agg.errors += count;
            }
        }

        int limit = Math.max(1, healthCheckProperties.getMaxTransactions());
        return byKey.values().stream()
                .sorted(Comparator.comparingLong((Agg a) -> a.count).reversed())
                .limit(limit)
                .map(a -> {
                    double avgMs = a.count > 0 ? a.totalMs / a.count : 0;
                    double errorRate = a.count > 0 ? (a.errors * 100.0) / a.count : 0;
                    return new ApmSnapshot.TransactionStats(
                            a.uri,
                            a.method,
                            a.count,
                            a.errors,
                            round2(errorRate),
                            round2(avgMs),
                            round2(a.maxMs),
                            ApmScoring.scoreFromAvg(avgMs, apdexThreshold)
                    );
                })
                .toList();
    }

    private ApmSnapshot.LoadStats readLoad() {
        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        double processCpu = -1;
        double systemCpu = -1;
        if (os instanceof OperatingSystemMXBean sunOs) {
            processCpu = clampLoad(sunOs.getProcessCpuLoad());
            systemCpu = clampLoad(sunOs.getCpuLoad());
        }
        return new ApmSnapshot.LoadStats(
                processCpu,
                systemCpu,
                round2(os.getSystemLoadAverage()),
                Runtime.getRuntime().availableProcessors()
        );
    }

    private static ApmSnapshot.GcStats readGc() {
        long count = 0;
        long time = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = gc.getCollectionCount();
            long t = gc.getCollectionTime();
            if (c > 0) {
                count += c;
            }
            if (t > 0) {
                time += t;
            }
        }
        return new ApmSnapshot.GcStats(count, time);
    }

    private static ApmSnapshot.ThreadStats readThreadStats(ThreadMXBean threads) {
        int runnable = 0;
        int blocked = 0;
        int waiting = 0;
        ThreadInfo[] infos = threads.getThreadInfo(threads.getAllThreadIds());
        if (infos != null) {
            for (ThreadInfo info : infos) {
                if (info == null || info.getThreadState() == null) {
                    continue;
                }
                switch (info.getThreadState()) {
                    case RUNNABLE -> runnable++;
                    case BLOCKED -> blocked++;
                    case WAITING, TIMED_WAITING -> waiting++;
                    default -> {
                    }
                }
            }
        }
        return new ApmSnapshot.ThreadStats(
                threads.getThreadCount(),
                threads.getPeakThreadCount(),
                threads.getDaemonThreadCount(),
                runnable,
                blocked,
                waiting
        );
    }

    private ApmSnapshot.HealthStats readHealth() {
        try {
            HealthComponent component = healthEndpoint.health();
            Map<String, String> parts = new LinkedHashMap<>();
            if (component instanceof SystemHealth systemHealth) {
                systemHealth.getComponents().forEach((name, child) ->
                        parts.put(name, statusOf(child)));
                return new ApmSnapshot.HealthStats(statusOf(systemHealth), parts);
            }
            return new ApmSnapshot.HealthStats(statusOf(component), parts);
        } catch (Exception ex) {
            return new ApmSnapshot.HealthStats("UNKNOWN", Map.of("error", ex.getClass().getSimpleName()));
        }
    }

    private ApmSnapshot.ProbeStats readProbes() {
        return new ApmSnapshot.ProbeStats(
                statusOfPath("liveness"),
                statusOfPath("readiness")
        );
    }

    private String statusOfPath(String path) {
        try {
            return statusOf(healthEndpoint.healthForPath(path));
        } catch (Exception ex) {
            return "UNKNOWN";
        }
    }

    private static String statusOf(HealthComponent component) {
        if (component == null) {
            return "UNKNOWN";
        }
        return component.getStatus().getCode();
    }

    private RequestBundle readRequests() {
        Collection<Timer> timers = meterRegistry.find("http.server.requests").timers();
        long total = 0;
        long errors = 0;
        double totalMs = 0;
        double maxMs = 0;
        for (Timer timer : timers) {
            String uri = timer.getId().getTag("uri");
            if (ApmUriFilters.isNoiseUri(uri)) {
                continue;
            }
            long count = timer.count();
            total += count;
            totalMs += timer.totalTime(TimeUnit.MILLISECONDS);
            maxMs = Math.max(maxMs, timer.max(TimeUnit.MILLISECONDS));
            String status = timer.getId().getTag("status");
            String outcome = timer.getId().getTag("outcome");
            if (isHttpError(status, outcome)) {
                errors += count;
            }
        }

        Instant now = Instant.now();
        RequestCounters current = new RequestCounters(total, errors, totalMs);
        Instant previousAt = previousSampleAt.get();
        double avgMs = total > 0 ? round2(totalMs / total) : 0.0;
        double overallErrorRate = total > 0 ? round2((errors * 100.0) / total) : 0.0;

        if (previousAt == null || current.total < previousCounters.get().total) {
            previousCounters.set(current);
            previousSampleAt.set(now);
            rpmWindow.clear();
            rpmWindow.observe(now.toEpochMilli(), total);
            return new RequestBundle(
                    new ApmSnapshot.RequestStats(total, errors, 0, 0, 0, overallErrorRate, avgMs),
                    round2(maxMs)
            );
        }

        RequestCounters previous = previousCounters.get();
        long reqDelta = Math.max(0, current.total - previous.total);
        long errDelta = Math.max(0, current.errors - previous.errors);
        double rpm = rpmWindow.observe(now.toEpochMilli(), total);
        double errorRate = reqDelta > 0 ? round2((errDelta * 100.0) / reqDelta) : overallErrorRate;

        previousCounters.set(current);
        previousSampleAt.set(now);
        return new RequestBundle(
                new ApmSnapshot.RequestStats(total, errors, reqDelta, errDelta, rpm, errorRate, avgMs),
                round2(maxMs)
        );
    }

    private List<ApmSnapshot.ThreadStack> readTopStacks(ThreadMXBean threads) {
        return readTopStacks(threads, TOP_STACK_THREADS, STACK_FRAMES);
    }

    private List<ApmSnapshot.ThreadStack> readTopStacks(ThreadMXBean threads, int limit, int frameLimit) {
        boolean cpuTimeEnabled = threads.isThreadCpuTimeSupported() && threads.isThreadCpuTimeEnabled();
        ThreadInfo[] infos = threads.dumpAllThreads(false, false);
        List<ThreadInfo> sorted = new ArrayList<>();
        for (ThreadInfo info : infos) {
            if (info != null) {
                sorted.add(info);
            }
        }
        sorted.sort(Comparator.comparingLong((ThreadInfo info) ->
                cpuTimeEnabled ? threads.getThreadCpuTime(info.getThreadId()) : 0L).reversed());

        List<ApmSnapshot.ThreadStack> stacks = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
            ThreadInfo info = sorted.get(i);
            long cpuNs = cpuTimeEnabled ? threads.getThreadCpuTime(info.getThreadId()) : 0L;
            long cpuMs = cpuNs > 0 ? cpuNs / 1_000_000L : 0L;
            List<String> frames = new ArrayList<>();
            StackTraceElement[] elements = info.getStackTrace();
            for (int f = 0; f < Math.min(frameLimit, elements.length); f++) {
                frames.add(elements[f].toString());
            }
            stacks.add(new ApmSnapshot.ThreadStack(
                    info.getThreadName(),
                    info.getThreadState().name(),
                    cpuMs,
                    frames
            ));
        }
        return stacks;
    }

    private static ApmSnapshot.MemoryStats toMemoryStats(MemoryUsage usage) {
        long used = usage.getUsed();
        long committed = usage.getCommitted();
        long max = usage.getMax();
        double percent = max > 0 ? (used * 100.0) / max : (committed > 0 ? (used * 100.0) / committed : 0.0);
        return new ApmSnapshot.MemoryStats(used, committed, max, round1(percent));
    }

    private static double clampLoad(double load) {
        if (load < 0) {
            return -1;
        }
        return round1(load * 100.0);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static boolean isHttpError(String status, String outcome) {
        if (outcome != null
                && ("SERVER_ERROR".equalsIgnoreCase(outcome) || "CLIENT_ERROR".equalsIgnoreCase(outcome))) {
            return true;
        }
        if (status != null && status.length() >= 1) {
            char c = status.charAt(0);
            return c == '4' || c == '5';
        }
        return false;
    }

    private static double round2(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            return value < 0 ? -1 : 0;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private record RequestCounters(long total, long errors, double totalMs) {
        static final RequestCounters ZERO = new RequestCounters(0, 0, 0);
    }

    private record RequestBundle(ApmSnapshot.RequestStats requests, double maxMs) {
    }

    private static final class Agg {
        final String uri;
        final String method;
        long count;
        long errors;
        double totalMs;
        double maxMs;

        Agg(String uri, String method) {
            this.uri = uri;
            this.method = method;
        }
    }
}
