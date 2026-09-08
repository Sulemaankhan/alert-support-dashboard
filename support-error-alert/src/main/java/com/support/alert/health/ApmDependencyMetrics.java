package com.support.alert.health;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds database aggregates and a New Relic–style service map from dependency scrapes. */
final class ApmDependencyMetrics {

    private static final int MAX_QUERIES = 16;
    private static final int MAX_EXTERNALS = 16;

    private ApmDependencyMetrics() {
    }

    static ApmSnapshot.DatabaseStats database(
            String status,
            String product,
            String validationQuery,
            List<ApmSnapshot.DatabasePoolStats> pools,
            List<ApmSnapshot.DatabaseQueryStats> queries) {
        List<ApmSnapshot.DatabasePoolStats> poolList = pools == null ? List.of() : List.copyOf(pools);
        List<ApmSnapshot.DatabaseQueryStats> queryList = queries == null ? List.of() : List.copyOf(queries);
        int active = 0;
        int idle = 0;
        int pending = 0;
        int max = 0;
        long timeouts = 0;
        for (ApmSnapshot.DatabasePoolStats pool : poolList) {
            active += Math.max(0, pool.active());
            idle += Math.max(0, pool.idle());
            pending += Math.max(0, pool.pending());
            max += Math.max(0, pool.max());
            timeouts += Math.max(0, pool.timeouts());
        }
        String resolved = status;
        if (resolved == null || resolved.isBlank() || "UNKNOWN".equalsIgnoreCase(resolved)) {
            if (!poolList.isEmpty() || !queryList.isEmpty() || (product != null && !product.isBlank())) {
                resolved = max > 0 && active >= max ? "DEGRADED" : "UP";
            } else {
                resolved = "UNKNOWN";
            }
        }
        return new ApmSnapshot.DatabaseStats(
                resolved,
                product == null ? "" : product,
                validationQuery == null ? "" : validationQuery,
                poolList,
                queryList,
                active,
                idle,
                pending,
                max,
                timeouts
        );
    }

    static List<ApmSnapshot.DatabaseQueryStats> limitQueries(List<ApmSnapshot.DatabaseQueryStats> queries) {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }
        return queries.stream()
                .sorted(Comparator.comparingLong(ApmSnapshot.DatabaseQueryStats::count).reversed())
                .limit(MAX_QUERIES)
                .toList();
    }

    static List<ApmSnapshot.ExternalServiceStats> limitExternals(List<ApmSnapshot.ExternalServiceStats> externals) {
        if (externals == null || externals.isEmpty()) {
            return List.of();
        }
        return externals.stream()
                .sorted(Comparator
                        .comparingLong(ApmSnapshot.ExternalServiceStats::count)
                        .reversed()
                        .thenComparing(ApmSnapshot.ExternalServiceStats::name, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_EXTERNALS)
                .toList();
    }

    static ApmSnapshot.ServiceMapStats serviceMap(
            String appName,
            String appStatus,
            ApmSnapshot.DatabaseStats database,
            List<ApmSnapshot.ExternalServiceStats> externals) {
        String appId = "app";
        List<ApmSnapshot.ServiceMapNode> nodes = new ArrayList<>();
        List<ApmSnapshot.ServiceMapEdge> edges = new ArrayList<>();
        nodes.add(new ApmSnapshot.ServiceMapNode(
                appId,
                appName == null || appName.isBlank() ? "application" : appName,
                "app",
                appStatus == null || appStatus.isBlank() ? "UNKNOWN" : appStatus,
                0,
                0,
                0,
                "Monitored service"
        ));

        if (database != null && database.hasSignal()) {
            String dbId = "db";
            String label = database.product() != null && !database.product().isBlank()
                    ? database.product()
                    : firstPoolName(database);
            String detail = database.max() > 0
                    ? database.active() + "/" + database.max() + " connections"
                    : (database.queries() == null || database.queries().isEmpty()
                    ? "Database health"
                    : database.queries().size() + " repository methods");
            long calls = 0;
            double weightedMs = 0;
            double weightedErr = 0;
            if (database.queries() != null) {
                for (ApmSnapshot.DatabaseQueryStats q : database.queries()) {
                    calls += Math.max(0, q.count());
                    weightedMs += Math.max(0, q.count()) * Math.max(0, q.avgMs());
                    weightedErr += Math.max(0, q.count()) * Math.max(0, q.errorRatePercent());
                }
            }
            double avgMs = calls > 0 ? round2(weightedMs / calls) : 0;
            double err = calls > 0 ? round2(weightedErr / calls) : 0;
            nodes.add(new ApmSnapshot.ServiceMapNode(
                    dbId,
                    label,
                    "database",
                    database.status(),
                    avgMs,
                    calls,
                    err,
                    detail
            ));
            edges.add(new ApmSnapshot.ServiceMapEdge(
                    appId,
                    dbId,
                    calls,
                    avgMs,
                    err,
                    edgeStatus(database.status(), err)
            ));
        }

        Map<String, ApmSnapshot.ExternalServiceStats> byNode = new LinkedHashMap<>();
        if (externals != null) {
            for (ApmSnapshot.ExternalServiceStats ext : externals) {
                String nodeId = externalNodeId(ext);
                byNode.merge(nodeId, ext, ApmDependencyMetrics::mergeExternal);
            }
        }
        for (Map.Entry<String, ApmSnapshot.ExternalServiceStats> entry : byNode.entrySet()) {
            ApmSnapshot.ExternalServiceStats ext = entry.getValue();
            String nodeId = entry.getKey();
            nodes.add(new ApmSnapshot.ServiceMapNode(
                    nodeId,
                    ext.name(),
                    ext.kind(),
                    ext.healthStatus() == null || ext.healthStatus().isBlank() ? "UNKNOWN" : ext.healthStatus(),
                    ext.avgMs(),
                    ext.count(),
                    ext.errorRatePercent(),
                    ext.target() != null && !ext.target().isBlank() ? ext.target() : ext.uri()
            ));
            edges.add(new ApmSnapshot.ServiceMapEdge(
                    appId,
                    nodeId,
                    ext.count(),
                    ext.avgMs(),
                    ext.errorRatePercent(),
                    edgeStatus(ext.healthStatus(), ext.errorRatePercent())
            ));
        }

        return new ApmSnapshot.ServiceMapStats(List.copyOf(nodes), List.copyOf(edges));
    }

    static double usagePercent(int active, int max) {
        if (max <= 0) {
            return 0;
        }
        return round1((active * 100.0) / max);
    }

    static double externalErrorRate(List<ApmSnapshot.ExternalServiceStats> externals) {
        if (externals == null || externals.isEmpty()) {
            return 0;
        }
        long count = 0;
        long errors = 0;
        for (ApmSnapshot.ExternalServiceStats ext : externals) {
            count += Math.max(0, ext.count());
            errors += Math.max(0, ext.errorCount());
        }
        if (count <= 0) {
            return 0;
        }
        return round2((errors * 100.0) / count);
    }

    static String classifyHealthComponent(String name) {
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (key.isBlank()) {
            return "other";
        }
        if (key.contains("db") || key.contains("mongo") || key.contains("cassandra") || key.contains("jdbc")) {
            return "database";
        }
        if (key.contains("redis") || key.contains("cache") || key.contains("hazelcast")) {
            return "cache";
        }
        if (key.contains("mail") || key.contains("smtp")) {
            return "mail";
        }
        if (key.contains("rabbit") || key.contains("kafka") || key.contains("jms") || key.contains("mq")) {
            return "queue";
        }
        if (key.contains("http") || key.contains("rest") || key.contains("client")) {
            return "http";
        }
        return "other";
    }

    static boolean isLocalInfraComponent(String name) {
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return key.equals("ping")
                || key.equals("diskspace")
                || key.equals("livenessstate")
                || key.equals("readinessstate")
                || key.equals("ssl")
                || key.equals("refreshscope")
                || key.equals("remote")
                || key.equals("error");
    }

    static String hostOf(String uri) {
        if (uri == null || uri.isBlank()) {
            return "";
        }
        String value = uri.trim();
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            String rest = value.substring(scheme + 3);
            int slash = rest.indexOf('/');
            return slash >= 0 ? rest.substring(0, slash) : rest;
        }
        return value;
    }

    static String displayName(String clientName, String uri) {
        if (clientName != null && !clientName.isBlank() && !"unknown".equalsIgnoreCase(clientName)) {
            return clientName.trim();
        }
        String host = hostOf(uri);
        return host.isBlank() ? (uri == null || uri.isBlank() ? "external" : uri) : host;
    }

    static double round1(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return Math.round(value * 10.0) / 10.0;
    }

    static double round2(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private static String firstPoolName(ApmSnapshot.DatabaseStats database) {
        if (database.pools() != null && !database.pools().isEmpty()) {
            String name = database.pools().get(0).name();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return "Database";
    }

    private static String externalNodeId(ApmSnapshot.ExternalServiceStats ext) {
        String raw = ext.name() != null && !ext.name().isBlank() ? ext.name() : ext.target();
        if (raw == null || raw.isBlank()) {
            raw = ext.uri();
        }
        if (raw == null || raw.isBlank()) {
            raw = ext.kind() + "-external";
        }
        return "ext-" + raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
    }

    private static ApmSnapshot.ExternalServiceStats mergeExternal(
            ApmSnapshot.ExternalServiceStats a,
            ApmSnapshot.ExternalServiceStats b) {
        long count = a.count() + b.count();
        long errors = a.errorCount() + b.errorCount();
        double totalMs = a.count() * a.avgMs() + b.count() * b.avgMs();
        return new ApmSnapshot.ExternalServiceStats(
                a.name(),
                a.kind(),
                a.target() != null && !a.target().isBlank() ? a.target() : b.target(),
                a.uri(),
                "*".equals(a.method()) ? b.method() : a.method(),
                count,
                errors,
                count > 0 ? round2((errors * 100.0) / count) : 0,
                count > 0 ? round2(totalMs / count) : 0,
                Math.max(a.maxMs(), b.maxMs()),
                worseStatus(a.healthStatus(), b.healthStatus())
        );
    }

    private static String edgeStatus(String status, double errorRatePercent) {
        if (status != null && "DOWN".equalsIgnoreCase(status)) {
            return "DOWN";
        }
        if (errorRatePercent >= 10 || (status != null && "OUT_OF_SERVICE".equalsIgnoreCase(status))) {
            return "DEGRADED";
        }
        if (status != null && !status.isBlank()) {
            return status;
        }
        return errorRatePercent > 0 ? "DEGRADED" : "UP";
    }

    private static String worseStatus(String a, String b) {
        return rank(a) >= rank(b) ? (a == null || a.isBlank() ? b : a) : b;
    }

    private static int rank(String status) {
        if (status == null) {
            return 0;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "DOWN" -> 4;
            case "OUT_OF_SERVICE" -> 3;
            case "DEGRADED" -> 2;
            case "UP" -> 1;
            default -> 0;
        };
    }
}
