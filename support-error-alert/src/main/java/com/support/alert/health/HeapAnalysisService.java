package com.support.alert.health;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class HeapAnalysisService {

    private static final int DEFAULT_CLASS_LIMIT = 80;

    private final HealthCheckProperties properties;
    private final RemoteActuatorClient actuatorClient;

    public HeapAnalysisService(HealthCheckProperties properties, RemoteActuatorClient actuatorClient) {
        this.properties = properties;
        this.actuatorClient = actuatorClient;
    }

    public HeapAnalysisView analyze(HealthTarget target) {
        if (target.isRemote()) {
            return analyzeRemote(target);
        }
        return analyzeLocal(target);
    }

    private HeapAnalysisView analyzeLocal(HealthTarget target) {
        Instant now = Instant.now();
        List<HeapAnalysisView.MemoryPoolUsage> pools = readLocalPools();
        Optional<String> histogram = JvmAttachHistogram.localDiagnosticHistogram();
        if (histogram.isEmpty()) {
            long pid = ProcessHandle.current().pid();
            histogram = JvmAttachHistogram.classHistogram(pid);
        }
        int limit = Math.max(10, properties.getMaxTransactions() * 6);
        List<HeapAnalysisView.ClassMemoryUsage> classes = histogram
                .map(text -> HeapHistogramParser.parse(text, limit))
                .orElse(List.of());
        long totalShallow = classes.stream().mapToLong(HeapAnalysisView.ClassMemoryUsage::shallowBytes).sum();
        String note = classes.isEmpty()
                ? "Per-class histogram unavailable on this JVM."
                : "Shallow bytes = all instances of each class (live GC histogram). True retained size needs a heap dump.";
        return new HeapAnalysisView(
                now,
                target.resolvedServiceName("local"),
                target.applicationId(),
                target.applicationName(),
                target.environmentId(),
                target.environmentLabel(),
                "local",
                "local",
                !classes.isEmpty(),
                note,
                pools,
                classes,
                totalShallow,
                classes.size()
        );
    }

    private HeapAnalysisView analyzeRemote(HealthTarget target) {
        Instant now = Instant.now();
        String base = target.normalizedActuatorBaseUrl();
        List<HeapAnalysisView.MemoryPoolUsage> pools = readRemotePools(base);
        int limit = Math.max(10, properties.getMaxTransactions() * 6);
        Optional<String> histogram = Optional.empty();
        String note = "Memory pools from Actuator metrics.";

        long metricPid = readRemotePid(base);
        long pid = SameHostJvmLocator.resolve(target, metricPid);
        if (pid > 0) {
            histogram = JvmAttachHistogram.classHistogram(pid);
            if (histogram.isPresent()) {
                note = "Per-class shallow usage from live GC.class_histogram on target PID "
                        + pid + " (same host). True retained size needs a heap dump.";
            } else {
                note += " Found PID " + pid + " but the class histogram command failed. "
                        + "Confirm this machine can run jcmd against that process.";
            }
        }
        if (histogram.isEmpty() && pid <= 0) {
            if (SameHostJvmLocator.isSameHost(base)) {
                note += " Could not resolve the target JVM PID on this host. "
                        + "Refresh after Drugstore is running, or analyze the local JVM.";
            } else {
                note += " Per-class histogram needs a same-host target (attach/jcmd) or a local JVM.";
            }
        }

        List<HeapAnalysisView.ClassMemoryUsage> classes = histogram
                .map(text -> HeapHistogramParser.parse(text, limit))
                .orElse(List.of());
        long totalShallow = classes.stream().mapToLong(HeapAnalysisView.ClassMemoryUsage::shallowBytes).sum();

        return new HeapAnalysisView(
                now,
                target.resolvedServiceName("remote"),
                target.applicationId(),
                target.applicationName(),
                target.environmentId(),
                target.environmentLabel(),
                "remote",
                base,
                !classes.isEmpty(),
                note,
                pools,
                classes,
                totalShallow,
                classes.size()
        );
    }

    private static List<HeapAnalysisView.MemoryPoolUsage> readLocalPools() {
        List<HeapAnalysisView.MemoryPoolUsage> pools = new ArrayList<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage usage = pool.getUsage();
            if (usage == null) {
                continue;
            }
            pools.add(toPool(pool.getName(), pool.getType().name(), usage));
        }
        pools.sort(Comparator.comparingLong(HeapAnalysisView.MemoryPoolUsage::usedBytes).reversed());
        return List.copyOf(pools);
    }

    private List<HeapAnalysisView.MemoryPoolUsage> readRemotePools(String base) {
        Optional<JsonNode> root = actuatorClient.getJson(base, "/metrics/jvm.memory.used");
        if (root.isEmpty()) {
            return List.of();
        }
        List<String> ids = tagValues(root.get(), "id");
        List<HeapAnalysisView.MemoryPoolUsage> pools = new ArrayList<>();
        for (String id : ids) {
            double used = metricValue(base, "jvm.memory.used", "id", id);
            if (used < 0) {
                continue;
            }
            double committed = metricValue(base, "jvm.memory.committed", "id", id);
            double max = metricValue(base, "jvm.memory.max", "id", id);
            pools.add(toPool(id, inferArea(id), used, committed, max));
        }
        pools.sort(Comparator.comparingLong(HeapAnalysisView.MemoryPoolUsage::usedBytes).reversed());
        return List.copyOf(pools);
    }

    private long readRemotePid(String base) {
        double pid = metricValue(base, "process.pid");
        if (pid > 0) {
            return (long) pid;
        }
        return 0L;
    }

    private double metricValue(String base, String name) {
        return metricStatistic(base, name, "VALUE", List.of());
    }

    private double metricValue(String base, String name, String tagKey, String tagValue) {
        return metricStatistic(base, name, "VALUE", List.of(tag(tagKey, tagValue)));
    }

    private double metricStatistic(String base, String name, String statistic, List<String> tags) {
        StringBuilder path = new StringBuilder("/metrics/").append(name);
        if (!tags.isEmpty()) {
            path.append('?');
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) {
                    path.append('&');
                }
                path.append(tags.get(i));
            }
        }
        Optional<JsonNode> node = actuatorClient.getJson(base, path.toString());
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
            JsonNode stat = measurement.get("statistic");
            if (stat != null && statistic.equalsIgnoreCase(stat.asText())) {
                double value = measurement.path("value").asDouble(Double.NaN);
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    return -1;
                }
                return value;
            }
        }
        return -1;
    }

    private static String inferArea(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        if (lower.contains("metaspace") || lower.contains("code cache") || lower.contains("compressed class")) {
            return "nonheap";
        }
        if (lower.contains("heap") || lower.contains("eden") || lower.contains("survivor")
                || lower.contains("old") || lower.contains("young") || lower.contains("g1")) {
            return "heap";
        }
        return "unknown";
    }

    private static List<String> tagValues(JsonNode metricNode, String tagName) {
        List<String> values = new ArrayList<>();
        JsonNode availableTags = metricNode.get("availableTags");
        if (availableTags == null || !availableTags.isArray()) {
            return values;
        }
        for (JsonNode tag : availableTags) {
            if (!tagName.equals(tag.path("tag").asText())) {
                continue;
            }
            JsonNode vals = tag.get("values");
            if (vals != null && vals.isArray()) {
                vals.forEach(v -> values.add(v.asText()));
            }
        }
        return values;
    }

    private static String tag(String key, String value) {
        return "tag=" + java.net.URLEncoder.encode(key + ":" + value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static HeapAnalysisView.MemoryPoolUsage toPool(String id, String area, MemoryUsage usage) {
        return toPool(id, area, usage.getUsed(), usage.getCommitted(), usage.getMax());
    }

    private static HeapAnalysisView.MemoryPoolUsage toPool(
            String id,
            String area,
            double used,
            double committed,
            double max) {
        long usedBytes = (long) Math.max(0, used);
        long committedBytes = (long) Math.max(0, committed);
        long maxBytes = max > 0 ? (long) max : -1;
        double percent = maxBytes > 0
                ? (usedBytes * 100.0) / maxBytes
                : (committedBytes > 0 ? (usedBytes * 100.0) / committedBytes : 0.0);
        return new HeapAnalysisView.MemoryPoolUsage(
                id,
                area,
                usedBytes,
                committedBytes,
                maxBytes,
                round1(percent)
        );
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
