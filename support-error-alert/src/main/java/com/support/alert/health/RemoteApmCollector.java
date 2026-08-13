package com.support.alert.health;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds an {@link ApmSnapshot} by scraping a remote Spring Boot Actuator base URL.
 */
@Component
public class RemoteApmCollector {

    private static final int MAX_SAMPLES = 120;
    private static final long TX_CACHE_MS = 4000L;

    private final HealthCheckProperties properties;
    private final RemoteActuatorClient client;
    private final List<ApmSnapshot.MetricSample> recentSamples = new ArrayList<>();
    private final AtomicReference<RequestCounters> previousCounters = new AtomicReference<>(RequestCounters.ZERO);
    private final AtomicReference<Instant> previousSampleAt = new AtomicReference<>(null);
    private volatile List<ApmSnapshot.TransactionStats> cachedTransactions = List.of();
    private volatile long cachedTransactionsAt;

    public RemoteApmCollector(HealthCheckProperties properties, RemoteActuatorClient client) {
        this.properties = properties;
        this.client = client;
    }

    public synchronized ApmSnapshot collect(boolean includeStacks) {
        String serviceName = properties.resolvedServiceName("remote-service");
        String targetUrl = properties.normalizedActuatorBaseUrl();
        double apdexThreshold = properties.getApdexThresholdMs() > 0 ? properties.getApdexThresholdMs() : 500;

        Optional<JsonNode> healthNode = client.getJson("/health");
        if (healthNode.isEmpty()) {
            return unreachableSnapshot(serviceName, targetUrl, "Unable to reach " + targetUrl, apdexThreshold);
        }

        ApmSnapshot.HealthStats health = parseHealth(healthNode.get());
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

        ApmSnapshot.GcStats gc = new ApmSnapshot.GcStats(
                (long) Math.max(0, metricStatistic("jvm.gc.pause", "COUNT")),
                (long) Math.max(0, metricStatistic("jvm.gc.pause", "TOTAL_TIME") * 1000.0)
        );

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

        RequestBundle requestBundle = readRequests();
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
                targetUrl,
                "remote"
        );
    }

    public synchronized List<ApmSnapshot.ThreadStack> fullStackDump() {
        return readStacks(40, 40);
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
        Optional<JsonNode> root = client.getJson("/metrics/http.server.requests");
        if (root.isEmpty()) {
            return List.of();
        }
        List<String> uris = tagValues(root.get(), "uri");
        // Drop Actuator/self-scrape templates so Top Transaction is real app traffic.
        uris.removeIf(ApmUriFilters::isNoiseUri);
        int limit = Math.max(1, properties.getMaxTransactions());
        if (uris.size() > limit * 2) {
            uris = uris.subList(0, limit * 2);
        }

        List<ApmSnapshot.TransactionStats> collected = new ArrayList<>();
        for (String uri : uris) {
            double count = metricStatistic("http.server.requests", "COUNT", "uri", uri);
            if (count <= 0) {
                continue;
            }
            double totalTimeSec = metricStatistic("http.server.requests", "TOTAL_TIME", "uri", uri);
            double maxSec = metricStatistic("http.server.requests", "MAX", "uri", uri);
            double errors = metricStatistic(
                    "http.server.requests",
                    "COUNT",
                    List.of(tag("uri", uri), tag("outcome", "SERVER_ERROR"))
            );
            if (errors < 0) {
                errors = 0;
            }
            double avgMs = count > 0 ? (totalTimeSec * 1000.0) / count : 0;
            double maxMs = maxSec > 0 ? maxSec * 1000.0 : avgMs;
            double errorRate = count > 0 ? (errors * 100.0) / count : 0;
            double apdex = ApmScoring.scoreFromAvg(avgMs, apdexThreshold);
            collected.add(new ApmSnapshot.TransactionStats(
                    uri,
                    "*",
                    (long) count,
                    (long) errors,
                    round2(errorRate),
                    round2(avgMs),
                    round2(maxMs),
                    apdex
            ));
        }

        collected.sort(Comparator.comparingLong(ApmSnapshot.TransactionStats::count).reversed());
        if (collected.size() > limit) {
            return List.copyOf(collected.subList(0, limit));
        }
        return List.copyOf(collected);
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
                new ApmSnapshot.GcStats(0, 0),
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
                targetUrl,
                "remote"
        );
    }

    private String parseProbeStatus(String path) {
        return client.getJson(path)
                .map(node -> text(node, "status", "UNKNOWN"))
                .orElse("UNKNOWN");
    }

    private ApmSnapshot.HealthStats parseHealth(JsonNode node) {
        String status = text(node, "status", "UNKNOWN");
        Map<String, String> components = new LinkedHashMap<>();
        JsonNode comps = node.get("components");
        if (comps != null && comps.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = comps.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                components.put(entry.getKey(), text(entry.getValue(), "status", "UNKNOWN"));
            }
        }
        return new ApmSnapshot.HealthStats(status, components);
    }

    private RequestBundle readRequests() {
        double total = metricStatistic("http.server.requests", "COUNT");
        double totalTimeSec = metricStatistic("http.server.requests", "TOTAL_TIME");
        double maxSec = metricStatistic("http.server.requests", "MAX");
        double errors = 0;
        for (String status : List.of("500", "501", "502", "503", "504")) {
            double part = metricStatistic("http.server.requests", "COUNT", "status", status);
            if (part > 0) {
                errors += part;
            }
        }
        double outcomeErrors = metricStatistic("http.server.requests", "COUNT", "outcome", "SERVER_ERROR");
        if (outcomeErrors > errors) {
            errors = outcomeErrors;
        }

        // Remove Actuator scrape traffic that dominates http.server.requests on the target.
        NoiseTraffic noise = measureNoiseTraffic();
        total = Math.max(0, total - noise.count);
        totalTimeSec = Math.max(0, totalTimeSec - noise.totalTimeSec);
        errors = Math.max(0, errors - noise.errors);

        Instant now = Instant.now();
        RequestCounters current = new RequestCounters((long) Math.max(0, total), (long) Math.max(0, errors), Math.max(0, totalTimeSec) * 1000.0);
        Instant previousAt = previousSampleAt.get();
        double avgMs = total > 0 ? round2((totalTimeSec * 1000.0) / total) : 0.0;
        double maxMs = maxSec > 0 ? round2(maxSec * 1000.0) : avgMs;

        if (previousAt == null) {
            previousCounters.set(current);
            previousSampleAt.set(now);
            return new RequestBundle(new ApmSnapshot.RequestStats(current.total, current.errors, 0, 0, 0, 0, avgMs), maxMs);
        }

        RequestCounters previous = previousCounters.get();
        long reqDelta = Math.max(0, current.total - previous.total);
        long errDelta = Math.max(0, current.errors - previous.errors);
        double seconds = Math.max(1.0, (now.toEpochMilli() - previousAt.toEpochMilli()) / 1000.0);
        double rpm = round2(reqDelta / (seconds / 60.0));
        double errorRate = reqDelta > 0 ? round2((errDelta * 100.0) / reqDelta) : 0.0;

        previousCounters.set(current);
        previousSampleAt.set(now);
        return new RequestBundle(
                new ApmSnapshot.RequestStats(current.total, current.errors, reqDelta, errDelta, rpm, errorRate, avgMs),
                maxMs
        );
    }

    private NoiseTraffic measureNoiseTraffic() {
        Optional<JsonNode> root = client.getJson("/metrics/http.server.requests");
        if (root.isEmpty()) {
            return NoiseTraffic.ZERO;
        }
        double count = 0;
        double totalTimeSec = 0;
        double errors = 0;
        for (String uri : tagValues(root.get(), "uri")) {
            if (ApmUriFilters.isAppUri(uri)) {
                continue;
            }
            double c = metricStatistic("http.server.requests", "COUNT", "uri", uri);
            if (c > 0) {
                count += c;
            }
            double t = metricStatistic("http.server.requests", "TOTAL_TIME", "uri", uri);
            if (t > 0) {
                totalTimeSec += t;
            }
            double e = metricStatistic(
                    "http.server.requests",
                    "COUNT",
                    List.of(tag("uri", uri), tag("outcome", "SERVER_ERROR"))
            );
            if (e > 0) {
                errors += e;
            }
        }
        return new NoiseTraffic(count, totalTimeSec, errors);
    }

    private List<ApmSnapshot.ThreadStack> readStacks(int limit, int frameLimit) {
        Optional<JsonNode> dump = client.getJson("/threaddump");
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
        Optional<JsonNode> node = client.getJson(path.toString());
        if (node.isEmpty()) {
            return -1;
        }
        JsonNode measurements = node.get().get("measurements");
        if (measurements == null || !measurements.isArray()) {
            return -1;
        }
        for (JsonNode measurement : measurements) {
            if (statistic.equalsIgnoreCase(text(measurement, "statistic", ""))) {
                return measurement.path("value").asDouble(-1);
            }
        }
        if (!measurements.isEmpty()) {
            return measurements.get(0).path("value").asDouble(-1);
        }
        return -1;
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

    private record NoiseTraffic(double count, double totalTimeSec, double errors) {
        static final NoiseTraffic ZERO = new NoiseTraffic(0, 0, 0);
    }
}
