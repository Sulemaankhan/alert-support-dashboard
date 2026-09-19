package com.support.alert.health;

import com.sun.management.OperatingSystemMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.SystemHealth;
import org.springframework.stereotype.Service;

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

    private static final int TOP_STACK_THREADS = 8;
    private static final int STACK_FRAMES = 12;

    private final String localServiceName;
    private final int serverPort;
    private final HealthCheckProperties healthCheckProperties;
    private final TargetApmRegistry targetApmRegistry;
    private final MeterRegistry meterRegistry;
    private final HealthEndpoint healthEndpoint;

    public ApmMetricsService(
            @Value("${spring.application.name:support-error-alert}") String localServiceName,
            @Value("${server.port:8081}") int serverPort,
            HealthCheckProperties healthCheckProperties,
            TargetApmRegistry targetApmRegistry,
            MeterRegistry meterRegistry,
            HealthEndpoint healthEndpoint) {
        this.localServiceName = localServiceName;
        this.serverPort = serverPort;
        this.healthCheckProperties = healthCheckProperties;
        this.targetApmRegistry = targetApmRegistry;
        this.meterRegistry = meterRegistry;
        this.healthEndpoint = healthEndpoint;
    }

    public synchronized ApmSnapshot snapshot(HealthTarget target) {
        return snapshot(target, true);
    }

    public synchronized ApmSnapshot snapshot(HealthTarget target, boolean includeStacks) {
        TargetApmSession session = targetApmRegistry.session(target);
        ApmSnapshot base = !target.usesLocalJvm(serverPort) && session.remoteCollector != null
                ? session.remoteCollector.collect(includeStacks)
                : localSnapshot(target, session, includeStacks);
        return base.withAlerts(session.alertTracker.evaluate(base));
    }

    public synchronized List<ApmSnapshot.ThreadStack> fullStackDump(HealthTarget target) {
        TargetApmSession session = targetApmRegistry.session(target);
        if (!target.usesLocalJvm(serverPort) && session.remoteCollector != null) {
            return session.remoteCollector.fullStackDump();
        }
        return readTopStacks(ManagementFactory.getThreadMXBean(), 40, 40);
    }

    private ApmSnapshot localSnapshot(HealthTarget target, TargetApmSession session, boolean includeStacks) {
        LocalTargetMetrics metrics = session.localMetrics;
        double apdexThreshold = healthCheckProperties.getApdexThresholdMs() > 0
                ? healthCheckProperties.getApdexThresholdMs()
                : 500;
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();

        ApmSnapshot.MemoryStats heap = toMemoryStats(memory.getHeapMemoryUsage());
        ApmSnapshot.MemoryStats nonHeap = toMemoryStats(memory.getNonHeapMemoryUsage());
        ApmSnapshot.LoadStats load = readLoad();
        ApmSnapshot.GcStats gc = metrics.gcMetricsTracker.build(GcMetricsTracker.fromLocalMxBeans());
        ApmSnapshot.ThreadStats threadStats = readThreadStats(threads);
        ApmSnapshot.HealthStats health = readHealth();
        ApmSnapshot.ProbeStats probes = readProbes();
        RequestBundle requestBundle = readRequests(metrics);
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
        ApmSnapshot.DatabaseStats database = readDatabase(health);
        List<ApmSnapshot.ExternalServiceStats> externals = readExternalServices(health);
        String serviceName = target.resolvedServiceName(localServiceName);
        String status = ApmScoring.deriveStatus(heap, requestBundle.requests(), health, probes, apdex, database);
        ApmSnapshot.ServiceMapStats serviceMap = ApmDependencyMetrics.serviceMap(
                serviceName, status, database, externals);

        Instant now = Instant.now();
        metrics.addSample(new ApmSnapshot.MetricSample(
                now,
                load.processCpuLoad(),
                heap.usedPercent(),
                nonHeap.usedPercent(),
                requestBundle.requests().requestsPerMinute(),
                requestBundle.requests().errorRatePercent(),
                requestBundle.requests().avgResponseTimeMs(),
                apdex.score(),
                gc.collectionCount(),
                gc.collectionTimeMs(),
                gc.collectionCountDelta(),
                gc.collectionTimeMsDelta(),
                ApmDependencyMetrics.usagePercent(database.active(), database.max()),
                ApmDependencyMetrics.externalErrorRate(externals)
        ));

        return new ApmSnapshot(
                status,
                now,
                serviceName,
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
                List.copyOf(metrics.recentSamples),
                database,
                externals,
                serviceMap,
                "local",
                "local",
                target.applicationId(),
                target.applicationName(),
                target.environmentId(),
                target.environmentLabel()
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

    private ApmSnapshot.DatabaseStats readDatabase(ApmSnapshot.HealthStats health) {
        DbIdentity identity = readDbIdentity(health);
        List<ApmSnapshot.DatabasePoolStats> pools = readPools();
        List<ApmSnapshot.DatabaseQueryStats> queries = readRepositoryQueries();
        return ApmDependencyMetrics.database(
                identity.status(),
                identity.product(),
                identity.validationQuery(),
                pools,
                queries
        );
    }

    private DbIdentity readDbIdentity(ApmSnapshot.HealthStats health) {
        String status = health != null && health.components() != null
                ? health.components().getOrDefault("db", "UNKNOWN")
                : "UNKNOWN";
        String product = "";
        String validation = "";
        try {
            HealthComponent component = healthEndpoint.healthForPath("db");
            if (component instanceof Health details) {
                status = details.getStatus().getCode();
                Object database = details.getDetails().get("database");
                Object query = details.getDetails().get("validationQuery");
                product = database != null ? String.valueOf(database) : "";
                validation = query != null ? String.valueOf(query) : "";
            } else if (component != null) {
                status = component.getStatus().getCode();
            }
        } catch (Exception ignored) {
            // no db indicator on this JVM
        }
        return new DbIdentity(status, product, validation);
    }

    private List<ApmSnapshot.DatabasePoolStats> readPools() {
        Map<String, PoolAgg> byName = new LinkedHashMap<>();
        collectPoolGauge(byName, "hikaricp.connections.active", "hikaricp", "active");
        collectPoolGauge(byName, "hikaricp.connections.idle", "hikaricp", "idle");
        collectPoolGauge(byName, "hikaricp.connections.pending", "hikaricp", "pending");
        collectPoolGauge(byName, "hikaricp.connections.min", "hikaricp", "min");
        collectPoolGauge(byName, "hikaricp.connections.max", "hikaricp", "max");
        collectPoolCounter(byName, "hikaricp.connections.timeout", "hikaricp");
        collectPoolTimer(byName, "hikaricp.connections.usage", "hikaricp", "usage");
        collectPoolTimer(byName, "hikaricp.connections.acquire", "hikaricp", "acquire");
        collectPoolGauge(byName, "jdbc.connections.active", "jdbc", "active");
        collectPoolGauge(byName, "jdbc.connections.min", "jdbc", "min");
        collectPoolGauge(byName, "jdbc.connections.max", "jdbc", "max");

        List<ApmSnapshot.DatabasePoolStats> pools = new ArrayList<>();
        for (PoolAgg agg : byName.values()) {
            pools.add(new ApmSnapshot.DatabasePoolStats(
                    agg.name,
                    agg.vendor,
                    (int) agg.active,
                    (int) agg.idle,
                    (int) agg.pending,
                    (int) agg.min,
                    (int) agg.max,
                    (long) agg.timeouts,
                    agg.usageCount > 0 ? round2(agg.usageMs / agg.usageCount) : 0,
                    agg.acquireCount > 0 ? round2(agg.acquireMs / agg.acquireCount) : 0,
                    ApmDependencyMetrics.usagePercent((int) agg.active, (int) agg.max)
            ));
        }
        return pools;
    }

    private void collectPoolGauge(Map<String, PoolAgg> byName, String metric, String vendor, String field) {
        for (Gauge gauge : meterRegistry.find(metric).gauges()) {
            PoolAgg agg = poolAgg(byName, gauge.getId().getTag("pool"), gauge.getId().getTag("name"), vendor);
            double value = gauge.value();
            if (Double.isNaN(value) || value < 0) {
                continue;
            }
            switch (field) {
                case "active" -> agg.active = value;
                case "idle" -> agg.idle = value;
                case "pending" -> agg.pending = value;
                case "min" -> agg.min = value;
                case "max" -> agg.max = value;
                default -> {
                }
            }
        }
    }

    private void collectPoolCounter(Map<String, PoolAgg> byName, String metric, String vendor) {
        meterRegistry.find(metric).counters().forEach(counter -> {
            PoolAgg agg = poolAgg(byName, counter.getId().getTag("pool"), counter.getId().getTag("name"), vendor);
            agg.timeouts += Math.max(0, counter.count());
        });
    }

    private void collectPoolTimer(Map<String, PoolAgg> byName, String metric, String vendor, String field) {
        for (Timer timer : meterRegistry.find(metric).timers()) {
            PoolAgg agg = poolAgg(byName, timer.getId().getTag("pool"), timer.getId().getTag("name"), vendor);
            long count = timer.count();
            double totalMs = timer.totalTime(TimeUnit.MILLISECONDS);
            if ("usage".equals(field)) {
                agg.usageCount += count;
                agg.usageMs += totalMs;
            } else {
                agg.acquireCount += count;
                agg.acquireMs += totalMs;
            }
        }
    }

    private static PoolAgg poolAgg(Map<String, PoolAgg> byName, String poolTag, String nameTag, String vendor) {
        String name = (poolTag != null && !poolTag.isBlank())
                ? poolTag
                : (nameTag != null && !nameTag.isBlank() ? nameTag : vendor);
        String key = vendor + ":" + name;
        return byName.computeIfAbsent(key, ignored -> new PoolAgg(name, vendor));
    }

    private List<ApmSnapshot.DatabaseQueryStats> readRepositoryQueries() {
        Map<String, QueryAgg> byKey = new HashMap<>();
        for (Timer timer : meterRegistry.find("spring.data.repository.invocations").timers()) {
            String repository = timer.getId().getTag("repository");
            String method = timer.getId().getTag("method");
            if (repository == null || repository.isBlank()) {
                continue;
            }
            String key = repository + "#" + (method == null ? "*" : method);
            QueryAgg agg = byKey.computeIfAbsent(key, ignored -> new QueryAgg(
                    simpleClassName(repository),
                    method == null || method.isBlank() ? "*" : method
            ));
            long count = timer.count();
            agg.count += count;
            agg.totalMs += timer.totalTime(TimeUnit.MILLISECONDS);
            agg.maxMs = Math.max(agg.maxMs, timer.max(TimeUnit.MILLISECONDS));
            String outcome = timer.getId().getTag("outcome");
            String exception = timer.getId().getTag("exception");
            if ((outcome != null && outcome.toUpperCase().contains("ERROR"))
                    || (exception != null && !"none".equalsIgnoreCase(exception) && !exception.isBlank())) {
                agg.errors += count;
            }
        }
        List<ApmSnapshot.DatabaseQueryStats> queries = new ArrayList<>();
        for (QueryAgg agg : byKey.values()) {
            queries.add(new ApmSnapshot.DatabaseQueryStats(
                    agg.repository,
                    agg.method,
                    agg.count,
                    agg.errors,
                    agg.count > 0 ? round2((agg.errors * 100.0) / agg.count) : 0,
                    agg.count > 0 ? round2(agg.totalMs / agg.count) : 0,
                    round2(agg.maxMs)
            ));
        }
        return ApmDependencyMetrics.limitQueries(queries);
    }

    private List<ApmSnapshot.ExternalServiceStats> readExternalServices(ApmSnapshot.HealthStats health) {
        Map<String, ExtAgg> byKey = new LinkedHashMap<>();
        for (Timer timer : meterRegistry.find("http.client.requests").timers()) {
            String uri = timer.getId().getTag("uri");
            String clientName = timer.getId().getTag("clientName");
            if (clientName == null) {
                clientName = timer.getId().getTag("client.name");
            }
            String method = timer.getId().getTag("method");
            String name = ApmDependencyMetrics.displayName(clientName, uri);
            String key = name + "|" + (uri == null ? "" : uri) + "|" + (method == null ? "*" : method);
            ExtAgg agg = byKey.computeIfAbsent(key, ignored -> new ExtAgg(
                    name,
                    "http",
                    ApmDependencyMetrics.hostOf(uri),
                    uri == null ? "" : uri,
                    method == null || method.isBlank() ? "*" : method
            ));
            long count = timer.count();
            agg.count += count;
            agg.totalMs += timer.totalTime(TimeUnit.MILLISECONDS);
            agg.maxMs = Math.max(agg.maxMs, timer.max(TimeUnit.MILLISECONDS));
            if (isHttpServerError(timer.getId().getTag("status"), timer.getId().getTag("outcome"))) {
                agg.errors += count;
            }
        }

        if (health != null && health.components() != null) {
            health.components().forEach((name, status) -> {
                if (ApmDependencyMetrics.isLocalInfraComponent(name) || "db".equalsIgnoreCase(name)) {
                    return;
                }
                String kind = ApmDependencyMetrics.classifyHealthComponent(name);
                String key = "health:" + name;
                byKey.computeIfAbsent(key, ignored -> new ExtAgg(name, kind, name, "", "*")).healthStatus = status;
            });
        }

        List<ApmSnapshot.ExternalServiceStats> list = new ArrayList<>();
        for (ExtAgg agg : byKey.values()) {
            String healthStatus = agg.healthStatus;
            if (healthStatus == null || healthStatus.isBlank()) {
                healthStatus = agg.errors > 0 && agg.count > 0 && (agg.errors * 100.0 / agg.count) >= 10
                        ? "DEGRADED"
                        : "UP";
            }
            list.add(new ApmSnapshot.ExternalServiceStats(
                    agg.name,
                    agg.kind,
                    agg.target,
                    agg.uri,
                    agg.method,
                    agg.count,
                    agg.errors,
                    agg.count > 0 ? round2((agg.errors * 100.0) / agg.count) : 0,
                    agg.count > 0 ? round2(agg.totalMs / agg.count) : 0,
                    round2(agg.maxMs),
                    healthStatus
            ));
        }
        return ApmDependencyMetrics.limitExternals(list);
    }

    private static String simpleClassName(String repository) {
        int dot = repository.lastIndexOf('.');
        return dot >= 0 ? repository.substring(dot + 1) : repository;
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

    private RequestBundle readRequests(LocalTargetMetrics metrics) {
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
        LocalTargetMetrics.RequestCounters current =
                new LocalTargetMetrics.RequestCounters(total, errors, totalMs);
        Instant previousAt = metrics.previousSampleAt.get();
        double avgMs = total > 0 ? round2(totalMs / total) : 0.0;
        double overallErrorRate = total > 0 ? round2((errors * 100.0) / total) : 0.0;

        if (previousAt == null || current.total() < metrics.previousCounters.get().total()) {
            metrics.previousCounters.set(current);
            metrics.previousSampleAt.set(now);
            metrics.rpmWindow.clear();
            metrics.rpmWindow.observe(now.toEpochMilli(), total);
            return new RequestBundle(
                    new ApmSnapshot.RequestStats(total, errors, 0, 0, 0, overallErrorRate, avgMs),
                    round2(maxMs)
            );
        }

        LocalTargetMetrics.RequestCounters previous = metrics.previousCounters.get();
        long reqDelta = Math.max(0, current.total() - previous.total());
        long errDelta = Math.max(0, current.errors() - previous.errors());
        double rpm = metrics.rpmWindow.observe(now.toEpochMilli(), total);
        double errorRate = reqDelta > 0 ? round2((errDelta * 100.0) / reqDelta) : overallErrorRate;

        metrics.previousCounters.set(current);
        metrics.previousSampleAt.set(now);
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

    private static boolean isHttpServerError(String status, String outcome) {
        if (outcome != null && "SERVER_ERROR".equalsIgnoreCase(outcome)) {
            return true;
        }
        return status != null && !status.isBlank() && status.charAt(0) == '5';
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

    private record RequestBundle(ApmSnapshot.RequestStats requests, double maxMs) {
    }

    private record DbIdentity(String status, String product, String validationQuery) {
    }

    private static final class PoolAgg {
        final String name;
        final String vendor;
        double active;
        double idle;
        double pending;
        double min;
        double max;
        double timeouts;
        double usageMs;
        long usageCount;
        double acquireMs;
        long acquireCount;

        PoolAgg(String name, String vendor) {
            this.name = name;
            this.vendor = vendor;
        }
    }

    private static final class QueryAgg {
        final String repository;
        final String method;
        long count;
        long errors;
        double totalMs;
        double maxMs;

        QueryAgg(String repository, String method) {
            this.repository = repository;
            this.method = method;
        }
    }

    private static final class ExtAgg {
        final String name;
        final String kind;
        final String target;
        final String uri;
        final String method;
        long count;
        long errors;
        double totalMs;
        double maxMs;
        String healthStatus = "";

        ExtAgg(String name, String kind, String target, String uri, String method) {
            this.name = name;
            this.kind = kind;
            this.target = target;
            this.uri = uri;
            this.method = method;
        }
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
