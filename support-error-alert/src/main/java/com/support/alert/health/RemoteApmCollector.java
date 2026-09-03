package com.support.alert.health;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds an {@link ApmSnapshot} by scraping a remote Spring Boot Actuator base URL.
 */
public class RemoteApmCollector {

    private static final int MAX_SAMPLES = 120;
    private static final long TX_CACHE_MS = 4000L;
    private static final long DEP_CACHE_MS = 4000L;
    private static final long RPM_WINDOW_MS = 300_000L;

    private final HealthTarget target;
    private final HealthCheckProperties properties;
    private final RemoteActuatorClient client;
    private final List<ApmSnapshot.MetricSample> recentSamples = new ArrayList<>();
    private final AtomicReference<RequestCounters> previousCounters = new AtomicReference<>(RequestCounters.ZERO);
    private final AtomicReference<Instant> previousSampleAt = new AtomicReference<>(null);
    private final RpmWindow rpmWindow = new RpmWindow(RPM_WINDOW_MS);
    private final GcMetricsTracker gcMetricsTracker = new GcMetricsTracker();
    private volatile List<ApmSnapshot.TransactionStats> cachedTransactions = List.of();
    private volatile long cachedTransactionsAt;
    private volatile DependencyBundle cachedDependencies = DependencyBundle.EMPTY;
    private volatile long cachedDependenciesAt;

    public RemoteApmCollector(HealthTarget target, HealthCheckProperties properties, RemoteActuatorClient client) {
        this.target = target;
        this.properties = properties;
        this.client = client;
    }

    public synchronized ApmSnapshot collect(boolean includeStacks) {
        String serviceName = target.resolvedServiceName("remote-service");
        String targetUrl = target.normalizedActuatorBaseUrl();
        double apdexThreshold = properties.getApdexThresholdMs() > 0 ? properties.getApdexThresholdMs() : 500;

        Optional<JsonNode> healthNode = getJson("/health");
        if (healthNode.isEmpty()) {
            return unreachableSnapshot(serviceName, targetUrl, "Unable to reach " + targetUrl, apdexThreshold);
        }

        HealthParse healthParse = parseHealth(healthNode.get());
        ApmSnapshot.HealthStats health = healthParse.health();
        ApmSnapshot.ProbeStats probes = new ApmSnapshot.ProbeStats(
                parseProbeStatus("/health/liveness"),
                parseProbeStatus("/health/readiness")
        );

        ApmSnapshot.MemoryStats heap = toMemoryStats(
                metricValue("jvm.memory.used", "area", "heap"),
                metricValue("jvm.memory.committed", "area", "heap"),
                metricValue("jvm.memory.max", "area", "heap")
        );
        ApmSnapshot.MemoryStats nonHeap = toMemoryStats(
                metricValue("jvm.memory.used", "area", "nonheap"),
                metricValue("jvm.memory.committed", "area", "nonheap"),
                metricValue("jvm.memory.max", "area", "nonheap")
        );

        ApmSnapshot.LoadStats load = new ApmSnapshot.LoadStats(
                toPercent(metricValue("process.cpu.usage")),
                toPercent(metricValue("system.cpu.usage")),
                round2(metricValue("system.load.average.1m")),
                (int) Math.max(0, metricValue("system.cpu.count"))
        );

        ApmSnapshot.GcStats gc = readGc();

        ApmSnapshot.ThreadStats threads = new ApmSnapshot.ThreadStats(
                (int) Math.max(0, metricValue("jvm.threads.live")),
                (int) Math.max(0, metricValue("jvm.threads.peak")),
                (int) Math.max(0, metricValue("jvm.threads.daemon")),
                (int) Math.max(0, metricValue("jvm.threads.states", "state", "runnable")),
                (int) Math.max(0, metricValue("jvm.threads.states", "state", "blocked")),
                (int) Math.max(
                        0,
                        metricValue("jvm.threads.states", "state", "waiting")
                                + metricValue("jvm.threads.states", "state", "timed-waiting")
                )
        );

        // RPM must use a fresh counter scrape every tick (transaction details can stay cached).
        RequestBundle requestBundle = readLiveAppRequestStats();
        List<ApmSnapshot.TransactionStats> transactions = loadTransactions(apdexThreshold);
        ApmSnapshot.ApdexStats apdex = transactions.isEmpty()
                ? ApmScoring.apdex(requestBundle.requests().avgResponseTimeMs(), apdexThreshold)
                : ApmScoring.weightedApdex(transactions, apdexThreshold);
        ApmSnapshot.LatencyStats latency = new ApmSnapshot.LatencyStats(
                requestBundle.requests().avgResponseTimeMs(),
                requestBundle.maxMs(),
                apdexThreshold
        );

        List<ApmSnapshot.ThreadStack> stacks = includeStacks ? readStacks(8, 12) : List.of();
        long uptimeMs = (long) Math.max(0, metricValue("process.uptime") * 1000.0);
        Instant now = Instant.now();
        DependencyBundle deps = loadDependencies(healthParse);

        String status = ApmScoring.deriveStatus(
                heap, requestBundle.requests(), health, probes, apdex, deps.database());
        ApmSnapshot.ServiceMapStats serviceMap = ApmDependencyMetrics.serviceMap(
                serviceName, status, deps.database(), deps.externals());

        recentSamples.add(new ApmSnapshot.MetricSample(
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
                ApmDependencyMetrics.usagePercent(deps.database().active(), deps.database().max()),
                ApmDependencyMetrics.externalErrorRate(deps.externals())
        ));
        while (recentSamples.size() > MAX_SAMPLES) {
            recentSamples.remove(0);
        }

        return new ApmSnapshot(
                status,
                now,
                serviceName,
                uptimeMs,
                load,
                heap,
                nonHeap,
                gc,
                threads,
                health,
                probes,
                requestBundle.requests(),
                latency,
                apdex,
                transactions,
                List.of(),
                stacks,
                List.copyOf(recentSamples),
                deps.database(),
                deps.externals(),
                serviceMap,
                targetUrl,
                "remote",
                target.applicationId(),
                target.applicationName(),
                target.environmentId(),
                target.environmentLabel()
        );
    }

    private Optional<JsonNode> getJson(String relativePath) {
        return client.getJson(target.normalizedActuatorBaseUrl(), relativePath);
    }

    public synchronized List<ApmSnapshot.ThreadStack> fullStackDump() {
        return readStacks(40, 40);
    }

    /**
     * Fresh every tick: sum app URI COUNTs quickly (errors use outcome tags only — no status spam).
     * Throughput RPM uses EWMA + 5-minute rolling window.
     */
    private RequestBundle readLiveAppRequestStats() {
        Optional<JsonNode> root = getJson("/metrics/http.server.requests");
        if (root.isEmpty()) {
            return new RequestBundle(new ApmSnapshot.RequestStats(0, 0, 0, 0, 0, 0, 0), 0);
        }

        long total = 0;
        long errors = 0;
        double timeSec = 0;
        double maxSec = 0;
        for (String uri : tagValues(root.get(), "uri")) {
            if (ApmUriFilters.isNoiseUri(uri)) {
                continue;
            }
            double count = positive(metricStatistic("http.server.requests", "COUNT", "uri", uri));
            if (count <= 0) {
                continue;
            }
            total += (long) count;
            timeSec += positive(metricTimeSeconds("http.server.requests", "uri", uri));
            maxSec = Math.max(maxSec, positive(metricStatistic("http.server.requests", "MAX", "uri", uri)));
            errors += (long) countUriErrorsFast(uri);
        }

        double avgMs = total > 0 ? round2((timeSec * 1000.0) / total) : 0.0;
        double maxMs = maxSec > 0 ? round2(maxSec * 1000.0) : avgMs;
        double overallErrorRate = total > 0 ? round2((errors * 100.0) / total) : 0.0;

        Instant now = Instant.now();
        RequestCounters current = new RequestCounters(total, errors, timeSec * 1000.0);
        Instant previousAt = previousSampleAt.get();
        RequestCounters previous = previousCounters.get();

        if (previousAt == null || current.total < previous.total) {
            previousCounters.set(current);
            previousSampleAt.set(now);
            rpmWindow.clear();
            double rpm = rpmWindow.observe(now.toEpochMilli(), total);
            return new RequestBundle(
                    new ApmSnapshot.RequestStats(total, errors, 0, 0, rpm, overallErrorRate, avgMs),
                    maxMs
            );
        }

        long reqDelta = current.total - previous.total;
        long errDelta = Math.max(0, current.errors - previous.errors);
        double rpm = rpmWindow.observe(now.toEpochMilli(), total);

        previousCounters.set(current);
        previousSampleAt.set(now);
        return new RequestBundle(
                new ApmSnapshot.RequestStats(
                        total,
                        errors,
                        reqDelta,
                        errDelta,
                        rpm,
                        reqDelta > 0 ? round2((errDelta * 100.0) / reqDelta) : overallErrorRate,
                        avgMs
                ),
                maxMs
        );
    }

    /** Outcome-only error count (avoids 15 status round-trips per URI on every tick). */
    private double countUriErrorsFast(String uri) {
        double server = positive(metricStatistic(
                "http.server.requests",
                "COUNT",
                List.of(tag("uri", uri), tag("outcome", "SERVER_ERROR"))
        ));
        double client = positive(metricStatistic(
                "http.server.requests",
                "COUNT",
                List.of(tag("uri", uri), tag("outcome", "CLIENT_ERROR"))
        ));
        return server + client;
    }

    private List<ApmSnapshot.TransactionStats> loadTransactions(double apdexThreshold) {
        long now = System.currentTimeMillis();
        if (now - cachedTransactionsAt < TX_CACHE_MS && cachedTransactions != null) {
            return cachedTransactions;
        }
        List<ApmSnapshot.TransactionStats> scraped = scrapeTransactions(apdexThreshold);
        cachedTransactions = scraped;
        cachedTransactionsAt = now;
        return scraped;
    }

    private List<ApmSnapshot.TransactionStats> scrapeTransactions(double apdexThreshold) {
        Optional<JsonNode> root = getJson("/metrics/http.server.requests");
        if (root.isEmpty()) {
            return List.of();
        }
        List<String> uris = tagValues(root.get(), "uri");
        uris.removeIf(ApmUriFilters::isNoiseUri);

        List<ApmSnapshot.TransactionStats> collected = new ArrayList<>();
        for (String uri : uris) {
            double count = positive(metricStatistic("http.server.requests", "COUNT", "uri", uri));
            if (count <= 0) {
                continue;
            }
            double uriTimeSec = positive(metricTimeSeconds("http.server.requests", "uri", uri));
            double uriMaxSec = positive(metricStatistic("http.server.requests", "MAX", "uri", uri));
            double errors = countUriErrors(uri);
            double avgMs = count > 0 ? (uriTimeSec * 1000.0) / count : 0;
            double uriMaxMs = uriMaxSec > 0 ? uriMaxSec * 1000.0 : avgMs;
            double errorRate = count > 0 ? (errors * 100.0) / count : 0;
            collected.add(new ApmSnapshot.TransactionStats(
                    uri,
                    "*",
                    (long) count,
                    (long) errors,
                    round2(errorRate),
                    round2(avgMs),
                    round2(uriMaxMs),
                    ApmScoring.scoreFromAvg(avgMs, apdexThreshold)
            ));
        }

        collected.sort(Comparator.comparingLong(ApmSnapshot.TransactionStats::count).reversed());
        int limit = Math.max(1, properties.getMaxTransactions());
        if (collected.size() > limit) {
            return List.copyOf(collected.subList(0, limit));
        }
        return List.copyOf(collected);
    }

    private double countUriErrors(String uri) {
        double server = positive(metricStatistic(
                "http.server.requests",
                "COUNT",
                List.of(tag("uri", uri), tag("outcome", "SERVER_ERROR"))
        ));
        double client = positive(metricStatistic(
                "http.server.requests",
                "COUNT",
                List.of(tag("uri", uri), tag("outcome", "CLIENT_ERROR"))
        ));
        double byOutcome = server + client;
        if (byOutcome > 0) {
            return byOutcome;
        }

        // Fallback when outcome tags are missing: any 4xx / 5xx status.
        double byStatus = 0;
        for (String status : List.of(
                "400", "401", "403", "404", "405", "408", "409", "415", "422", "429",
                "500", "501", "502", "503", "504")) {
            double part = metricStatistic(
                    "http.server.requests",
                    "COUNT",
                    List.of(tag("uri", uri), tag("status", status))
            );
            if (part > 0) {
                byStatus += part;
            }
        }
        return byStatus;
    }

    private ApmSnapshot unreachableSnapshot(
            String serviceName,
            String targetUrl,
            String reason,
            double apdexThreshold) {
        Instant now = Instant.now();
        ApmSnapshot.MemoryStats emptyMem = new ApmSnapshot.MemoryStats(0, 0, 0, 0);
        ApmSnapshot.RequestStats emptyReq = new ApmSnapshot.RequestStats(0, 0, 0, 0, 0, 0, 0);
        ApmSnapshot.ApdexStats apdex = new ApmSnapshot.ApdexStats(0, "Unacceptable", apdexThreshold);
        return new ApmSnapshot(
                "DOWN",
                now,
                serviceName,
                0,
                new ApmSnapshot.LoadStats(-1, -1, -1, 0),
                emptyMem,
                emptyMem,
                new ApmSnapshot.GcStats(0, 0, 0, 0, List.of()),
                new ApmSnapshot.ThreadStats(0, 0, 0, 0, 0, 0),
                new ApmSnapshot.HealthStats("DOWN", Map.of("remote", reason)),
                new ApmSnapshot.ProbeStats("DOWN", "DOWN"),
                emptyReq,
                new ApmSnapshot.LatencyStats(0, 0, apdexThreshold),
                apdex,
                List.of(),
                List.of(),
                List.of(),
                List.copyOf(recentSamples),
                ApmSnapshot.DatabaseStats.empty(),
                List.of(),
                ApmSnapshot.ServiceMapStats.empty(),
                targetUrl,
                "remote",
                target.applicationId(),
                target.applicationName(),
                target.environmentId(),
                target.environmentLabel()
        );
    }

    private ApmSnapshot.GcStats readGc() {
        List<ApmSnapshot.GcCollectorStats> collectors = new ArrayList<>();
        Optional<JsonNode> root = getJson("/metrics/jvm.gc.pause");
        if (root.isPresent()) {
            String tagKey = "gc";
            List<String> names = tagValues(root.get(), "gc");
            if (names.isEmpty()) {
                tagKey = "name";
                names = tagValues(root.get(), "name");
            }
            for (String name : names) {
                long count = (long) positive(metricStatistic("jvm.gc.pause", "COUNT", tagKey, name));
                long timeMs = (long) (positive(metricTimeSeconds("jvm.gc.pause", tagKey, name)) * 1000.0);
                collectors.add(new ApmSnapshot.GcCollectorStats(name, count, timeMs, 0, 0));
            }
        }
        if (collectors.isEmpty()) {
            long count = (long) Math.max(0, positive(metricStatistic("jvm.gc.pause", "COUNT")));
            long timeMs = (long) Math.max(0, positive(metricTimeSeconds("jvm.gc.pause")) * 1000.0);
            collectors.add(new ApmSnapshot.GcCollectorStats("all", count, timeMs, 0, 0));
        }
        return gcMetricsTracker.build(collectors);
    }

    private String parseProbeStatus(String path) {
        return getJson(path)
                .map(node -> text(node, "status", "UNKNOWN"))
                .orElse("UNKNOWN");
    }

    private HealthParse parseHealth(JsonNode node) {
        String status = text(node, "status", "UNKNOWN");
        Map<String, String> components = new LinkedHashMap<>();
        String dbProduct = "";
        String dbValidation = "";
        String dbStatus = "UNKNOWN";
        JsonNode comps = node.get("components");
        if (comps != null && comps.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = comps.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                components.put(entry.getKey(), text(entry.getValue(), "status", "UNKNOWN"));
                if ("db".equalsIgnoreCase(entry.getKey())) {
                    dbStatus = text(entry.getValue(), "status", "UNKNOWN");
                    JsonNode details = entry.getValue().get("details");
                    if (details != null && details.isObject()) {
                        dbProduct = text(details, "database", "");
                        dbValidation = text(details, "validationQuery", "");
                    }
                }
            }
        }
        return new HealthParse(
                new ApmSnapshot.HealthStats(status, components),
                dbStatus,
                dbProduct,
                dbValidation
        );
    }

    private DependencyBundle loadDependencies(HealthParse healthParse) {
        long now = System.currentTimeMillis();
        if (now - cachedDependenciesAt < DEP_CACHE_MS && cachedDependencies != null) {
            return cachedDependencies;
        }
        DependencyBundle scraped = scrapeDependencies(healthParse);
        cachedDependencies = scraped;
        cachedDependenciesAt = now;
        return scraped;
    }

    private DependencyBundle scrapeDependencies(HealthParse healthParse) {
        List<ApmSnapshot.DatabasePoolStats> pools = scrapePools();
        List<ApmSnapshot.DatabaseQueryStats> queries = scrapeRepositoryQueries();
        ApmSnapshot.DatabaseStats database = ApmDependencyMetrics.database(
                healthParse.dbStatus(),
                healthParse.dbProduct(),
                healthParse.dbValidation(),
                pools,
                queries
        );
        List<ApmSnapshot.ExternalServiceStats> externals = scrapeExternalServices(healthParse.health());
        return new DependencyBundle(database, externals);
    }

    private List<ApmSnapshot.DatabasePoolStats> scrapePools() {
        List<ApmSnapshot.DatabasePoolStats> hikari = scrapeNamedPools(
                "hikaricp.connections.active",
                "pool",
                "hikaricp"
        );
        if (!hikari.isEmpty()) {
            return hikari;
        }
        return scrapeNamedPools("jdbc.connections.active", "name", "jdbc");
    }

    private List<ApmSnapshot.DatabasePoolStats> scrapeNamedPools(String activeMetric, String tagKey, String vendor) {
        Optional<JsonNode> root = getJson("/metrics/" + activeMetric);
        if (root.isEmpty()) {
            return List.of();
        }
        List<String> names = tagValues(root.get(), tagKey);
        if (names.isEmpty()) {
            names = List.of("");
        }
        String prefix = vendor + ".connections.";
        boolean hikari = "hikaricp".equals(vendor);
        List<ApmSnapshot.DatabasePoolStats> pools = new ArrayList<>();
        for (String name : names) {
            List<String> tags = name.isBlank() ? List.of() : List.of(tag(tagKey, name));
            int active = (int) positive(metricStatistic(prefix + "active", "VALUE", tags));
            int min = (int) positive(metricStatistic(prefix + "min", "VALUE", tags));
            int max = (int) positive(metricStatistic(prefix + "max", "VALUE", tags));
            int idle = 0;
            int pending = 0;
            long timeouts = 0;
            double usageAvgMs = 0;
            double acquireAvgMs = 0;
            if (hikari) {
                idle = (int) positive(metricStatistic(prefix + "idle", "VALUE", tags));
                pending = (int) positive(metricStatistic(prefix + "pending", "VALUE", tags));
                timeouts = (long) positive(metricStatistic(prefix + "timeout", "COUNT", tags));
                double usageCount = positive(metricStatistic(prefix + "usage", "COUNT", tags));
                double usageSec = positive(metricTimeSeconds(prefix + "usage", tagKey, name.isBlank() ? null : name));
                double acquireCount = positive(metricStatistic(prefix + "acquire", "COUNT", tags));
                double acquireSec = positive(metricTimeSeconds(prefix + "acquire", tagKey, name.isBlank() ? null : name));
                usageAvgMs = usageCount > 0 ? ApmDependencyMetrics.round2((usageSec * 1000.0) / usageCount) : 0;
                acquireAvgMs = acquireCount > 0 ? ApmDependencyMetrics.round2((acquireSec * 1000.0) / acquireCount) : 0;
            }
            if (active <= 0 && idle <= 0 && max <= 0) {
                continue;
            }
            pools.add(new ApmSnapshot.DatabasePoolStats(
                    name.isBlank() ? vendor : name,
                    vendor,
                    active,
                    idle,
                    pending,
                    min,
                    max,
                    timeouts,
                    usageAvgMs,
                    acquireAvgMs,
                    ApmDependencyMetrics.usagePercent(active, max)
            ));
        }
        return pools;
    }

    private List<ApmSnapshot.DatabaseQueryStats> scrapeRepositoryQueries() {
        Optional<JsonNode> root = getJson("/metrics/spring.data.repository.invocations");
        if (root.isEmpty()) {
            return List.of();
        }
        List<String> repositories = tagValues(root.get(), "repository");
        if (repositories.isEmpty()) {
            return List.of();
        }
        Map<String, QueryRemoteAgg> byKey = new HashMap<>();
        for (String repository : repositories) {
            List<String> methods = tagValues(
                    getJson("/metrics/spring.data.repository.invocations?" + tag("repository", repository))
                            .orElse(root.get()),
                    "method"
            );
            if (methods.isEmpty()) {
                methods = List.of("");
            }
            for (String method : methods) {
                List<String> tags = new ArrayList<>();
                tags.add(tag("repository", repository));
                if (!method.isBlank()) {
                    tags.add(tag("method", method));
                }
                double count = positive(metricStatistic("spring.data.repository.invocations", "COUNT", tags));
                if (count <= 0) {
                    continue;
                }
                double timeSec = positive(metricTimeSeconds(
                        "spring.data.repository.invocations",
                        method.isBlank()
                                ? List.of(tag("repository", repository))
                                : List.of(tag("repository", repository), tag("method", method))
                ));
                double maxSec = positive(metricStatistic("spring.data.repository.invocations", "MAX", tags));
                double errors = repositoryErrors(repository, method);
                String key = repository + "#" + method;
                QueryRemoteAgg agg = byKey.computeIfAbsent(key, ignored -> new QueryRemoteAgg(
                        simpleClassName(repository),
                        method.isBlank() ? "*" : method
                ));
                agg.count += (long) count;
                agg.errors += (long) errors;
                agg.totalMs += timeSec * 1000.0;
                agg.maxMs = Math.max(agg.maxMs, maxSec * 1000.0);
            }
        }
        List<ApmSnapshot.DatabaseQueryStats> queries = new ArrayList<>();
        for (QueryRemoteAgg agg : byKey.values()) {
            queries.add(new ApmSnapshot.DatabaseQueryStats(
                    agg.repository,
                    agg.method,
                    agg.count,
                    agg.errors,
                    agg.count > 0 ? ApmDependencyMetrics.round2((agg.errors * 100.0) / agg.count) : 0,
                    agg.count > 0 ? ApmDependencyMetrics.round2(agg.totalMs / agg.count) : 0,
                    ApmDependencyMetrics.round2(agg.maxMs)
            ));
        }
        return ApmDependencyMetrics.limitQueries(queries);
    }

    private double repositoryErrors(String repository, String method) {
        List<String> tags = new ArrayList<>();
        tags.add(tag("repository", repository));
        if (method != null && !method.isBlank()) {
            tags.add(tag("method", method));
        }
        tags.add(tag("outcome", "SERVER_ERROR"));
        double server = positive(metricStatistic("spring.data.repository.invocations", "COUNT", tags));
        tags.set(tags.size() - 1, tag("outcome", "CLIENT_ERROR"));
        return server + positive(metricStatistic("spring.data.repository.invocations", "COUNT", tags));
    }

    private List<ApmSnapshot.ExternalServiceStats> scrapeExternalServices(ApmSnapshot.HealthStats health) {
        Map<String, ExtRemoteAgg> byKey = new LinkedHashMap<>();
        Optional<JsonNode> root = getJson("/metrics/http.client.requests");
        if (root.isPresent()) {
            List<String> uris = tagValues(root.get(), "uri");
            List<String> clients = tagValues(root.get(), "clientName");
            if (clients.isEmpty()) {
                clients = tagValues(root.get(), "client.name");
            }
            if (!uris.isEmpty()) {
                for (String uri : uris) {
                    double count = positive(metricStatistic("http.client.requests", "COUNT", "uri", uri));
                    if (count <= 0) {
                        continue;
                    }
                    double timeSec = positive(metricTimeSeconds("http.client.requests", "uri", uri));
                    double maxSec = positive(metricStatistic("http.client.requests", "MAX", "uri", uri));
                    double errors = countClientErrors(uri);
                    String name = ApmDependencyMetrics.displayName(null, uri);
                    ExtRemoteAgg agg = byKey.computeIfAbsent(name + "|" + uri, ignored -> new ExtRemoteAgg(
                            name, "http", ApmDependencyMetrics.hostOf(uri), uri, "*"
                    ));
                    agg.count += (long) count;
                    agg.errors += (long) errors;
                    agg.totalMs += timeSec * 1000.0;
                    agg.maxMs = Math.max(agg.maxMs, maxSec * 1000.0);
                }
            } else if (!clients.isEmpty()) {
                for (String client : clients) {
                    double count = positive(metricStatistic("http.client.requests", "COUNT", "clientName", client));
                    if (count <= 0) {
                        count = positive(metricStatistic("http.client.requests", "COUNT", "client.name", client));
                    }
                    if (count <= 0) {
                        continue;
                    }
                    double timeSec = positive(metricTimeSeconds("http.client.requests", "clientName", client));
                    double maxSec = positive(metricStatistic("http.client.requests", "MAX", "clientName", client));
                    ExtRemoteAgg agg = byKey.computeIfAbsent(client, ignored -> new ExtRemoteAgg(
                            client, "http", client, "", "*"
                    ));
                    agg.count += (long) count;
                    agg.totalMs += timeSec * 1000.0;
                    agg.maxMs = Math.max(agg.maxMs, maxSec * 1000.0);
                }
            }
        }

        if (health != null && health.components() != null) {
            health.components().forEach((name, status) -> {
                if (ApmDependencyMetrics.isLocalInfraComponent(name) || "db".equalsIgnoreCase(name)) {
                    return;
                }
                String kind = ApmDependencyMetrics.classifyHealthComponent(name);
                ExtRemoteAgg agg = byKey.computeIfAbsent("health:" + name, ignored -> new ExtRemoteAgg(
                        name, kind, name, "", "*"
                ));
                agg.healthStatus = status;
            });
        }

        List<ApmSnapshot.ExternalServiceStats> list = new ArrayList<>();
        for (ExtRemoteAgg agg : byKey.values()) {
            String healthStatus = agg.healthStatus;
            if (healthStatus == null || healthStatus.isBlank()) {
                healthStatus = agg.count > 0 && agg.errors * 100.0 / Math.max(1, agg.count) >= 10
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
                    agg.count > 0 ? ApmDependencyMetrics.round2((agg.errors * 100.0) / agg.count) : 0,
                    agg.count > 0 ? ApmDependencyMetrics.round2(agg.totalMs / agg.count) : 0,
                    ApmDependencyMetrics.round2(agg.maxMs),
                    healthStatus
            ));
        }
        return ApmDependencyMetrics.limitExternals(list);
    }

    private double countClientErrors(String uri) {
        return positive(metricStatistic(
                "http.client.requests",
                "COUNT",
                List.of(tag("uri", uri), tag("outcome", "SERVER_ERROR"))
        ));
    }

    private double metricTimeSeconds(String name, List<String> tags) {
        double totalTime = metricStatistic(name, "TOTAL_TIME", tags);
        if (totalTime >= 0 && !Double.isNaN(totalTime)) {
            return totalTime;
        }
        double total = metricStatistic(name, "TOTAL", tags);
        if (total >= 0 && !Double.isNaN(total)) {
            return total;
        }
        return 0;
    }

    private static String simpleClassName(String repository) {
        int dot = repository.lastIndexOf('.');
        return dot >= 0 ? repository.substring(dot + 1) : repository;
    }

    private List<ApmSnapshot.ThreadStack> readStacks(int limit, int frameLimit) {
        Optional<JsonNode> dump = getJson("/threaddump");
        if (dump.isEmpty()) {
            return List.of();
        }
        JsonNode threads = dump.get().get("threads");
        if (threads == null || !threads.isArray()) {
            return List.of();
        }

        List<JsonNode> list = new ArrayList<>();
        threads.forEach(list::add);
        list.sort(Comparator.comparingLong((JsonNode n) -> n.path("threadCpuTime").asLong(0L)).reversed());

        List<ApmSnapshot.ThreadStack> stacks = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, list.size()); i++) {
            JsonNode t = list.get(i);
            String name = text(t, "threadName", "thread");
            String state = text(t, "threadState", "UNKNOWN");
            long cpuNs = t.path("threadCpuTime").asLong(0L);
            long cpuMs = cpuNs > 0 ? cpuNs / 1_000_000L : 0L;
            List<String> frames = new ArrayList<>();
            JsonNode stack = t.get("stackTrace");
            if (stack != null && stack.isArray()) {
                for (int f = 0; f < Math.min(frameLimit, stack.size()); f++) {
                    frames.add(formatFrame(stack.get(f)));
                }
            }
            stacks.add(new ApmSnapshot.ThreadStack(name, state, cpuMs, frames));
        }
        return stacks;
    }

    private static String formatFrame(JsonNode frame) {
        String cls = text(frame, "className", "?");
        String method = text(frame, "methodName", "?");
        String file = text(frame, "fileName", "Unknown Source");
        int line = frame.path("lineNumber").asInt(-1);
        if (line >= 0) {
            return cls + "." + method + "(" + file + ":" + line + ")";
        }
        return cls + "." + method + "(" + file + ")";
    }

    private double metricValue(String name) {
        return metricStatistic(name, "VALUE");
    }

    private double metricValue(String name, String tagKey, String tagValue) {
        return metricStatistic(name, "VALUE", tagKey, tagValue);
    }

    private double metricStatistic(String name, String statistic) {
        return metricStatistic(name, statistic, List.of());
    }

    private double metricStatistic(String name, String statistic, String tagKey, String tagValue) {
        return metricStatistic(name, statistic, List.of(tag(tagKey, tagValue)));
    }

    private double metricTimeSeconds(String name) {
        return metricTimeSeconds(name, null, null);
    }

    private double metricTimeSeconds(String name, String tagKey, String tagValue) {
        List<String> tags = (tagKey == null || tagValue == null)
                ? List.of()
                : List.of(tag(tagKey, tagValue));
        double totalTime = metricStatistic(name, "TOTAL_TIME", tags);
        if (totalTime >= 0 && !Double.isNaN(totalTime)) {
            return totalTime;
        }
        // Some registries expose TOTAL instead of TOTAL_TIME.
        double total = metricStatistic(name, "TOTAL", tags);
        if (total >= 0 && !Double.isNaN(total)) {
            return total;
        }
        return 0;
    }

    private double metricStatistic(String name, String statistic, List<String> tags) {
        StringBuilder path = new StringBuilder("/metrics/").append(name);
        if (tags != null && !tags.isEmpty()) {
            path.append('?');
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) {
                    path.append('&');
                }
                path.append(tags.get(i));
            }
        }
        Optional<JsonNode> node = getJson(path.toString());
        if (node.isEmpty()) {
            return -1;
        }
        return measurement(node.get(), statistic);
    }

    private static double measurement(JsonNode metricNode, String statistic) {
        JsonNode measurements = metricNode.get("measurements");
        if (measurements == null || !measurements.isArray()) {
            return -1;
        }
        for (JsonNode measurement : measurements) {
            if (statistic.equalsIgnoreCase(text(measurement, "statistic", ""))) {
                double value = measurement.path("value").asDouble(Double.NaN);
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    return -1;
                }
                return value;
            }
        }
        return -1;
    }

    private static double positive(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            return 0;
        }
        return value;
    }

    private static String tag(String key, String value) {
        return "tag=" + URLEncoder.encode(key + ":" + value, StandardCharsets.UTF_8);
    }

    private static List<String> tagValues(JsonNode metricNode, String tagName) {
        List<String> values = new ArrayList<>();
        JsonNode availableTags = metricNode.get("availableTags");
        if (availableTags == null || !availableTags.isArray()) {
            return values;
        }
        for (JsonNode tag : availableTags) {
            if (!tagName.equals(text(tag, "tag", ""))) {
                continue;
            }
            JsonNode vals = tag.get("values");
            if (vals != null && vals.isArray()) {
                vals.forEach(v -> values.add(v.asText()));
            }
        }
        return values;
    }

    private static ApmSnapshot.MemoryStats toMemoryStats(double used, double committed, double max) {
        long usedBytes = (long) Math.max(0, used);
        long committedBytes = (long) Math.max(0, committed);
        long maxBytes = max > 0 ? (long) max : -1;
        double percent = maxBytes > 0
                ? (usedBytes * 100.0) / maxBytes
                : (committedBytes > 0 ? (usedBytes * 100.0) / committedBytes : 0.0);
        return new ApmSnapshot.MemoryStats(usedBytes, committedBytes, maxBytes, round1(percent));
    }

    private static double toPercent(double ratio) {
        if (ratio < 0) {
            return -1;
        }
        return round1(ratio * 100.0);
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? defaultValue : text;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double round2(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        if (value < 0) {
            return -1;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private record RequestCounters(long total, long errors, double totalMs) {
        static final RequestCounters ZERO = new RequestCounters(0, 0, 0);
    }

    private record RequestBundle(ApmSnapshot.RequestStats requests, double maxMs) {
    }

    private record HealthParse(
            ApmSnapshot.HealthStats health,
            String dbStatus,
            String dbProduct,
            String dbValidation
    ) {
    }

    private record DependencyBundle(
            ApmSnapshot.DatabaseStats database,
            List<ApmSnapshot.ExternalServiceStats> externals
    ) {
        static final DependencyBundle EMPTY = new DependencyBundle(
                ApmSnapshot.DatabaseStats.empty(),
                List.of()
        );
    }

    private static final class QueryRemoteAgg {
        final String repository;
        final String method;
        long count;
        long errors;
        double totalMs;
        double maxMs;

        QueryRemoteAgg(String repository, String method) {
            this.repository = repository;
            this.method = method;
        }
    }

    private static final class ExtRemoteAgg {
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

        ExtRemoteAgg(String name, String kind, String target, String uri, String method) {
            this.name = name;
            this.kind = kind;
            this.target = target;
            this.uri = uri;
            this.method = method;
        }
    }
}
