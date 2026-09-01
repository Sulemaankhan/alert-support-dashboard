package com.support.alert.health;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses HotSpot {@code gcClassHistogram} / {@code GC.class_histogram} text output. */
final class HeapHistogramParser {

    private static final Pattern LINE = Pattern.compile(
            "^\\s*(\\d+):\\s+(\\d+)\\s+(\\d+)\\s+(.+?)\\s*$");

    private HeapHistogramParser() {
    }

    static List<HeapAnalysisView.ClassMemoryUsage> parse(String raw, int limit) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<HeapAnalysisView.ClassMemoryUsage> rows = new ArrayList<>();
        long totalBytes = 0;
        for (String line : raw.split("\\R")) {
            Matcher matcher = LINE.matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }
            int rank = Integer.parseInt(matcher.group(1));
            long instances = Long.parseLong(matcher.group(2));
            long bytes = Long.parseLong(matcher.group(3));
            String className = cleanClassName(matcher.group(4));
            rows.add(new HeapAnalysisView.ClassMemoryUsage(rank, className, instances, bytes, 0));
            totalBytes += bytes;
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        rows.sort(Comparator.comparingLong(HeapAnalysisView.ClassMemoryUsage::shallowBytes).reversed());
        int cap = Math.max(1, limit);
        List<HeapAnalysisView.ClassMemoryUsage> top = new ArrayList<>();
        for (int i = 0; i < Math.min(cap, rows.size()); i++) {
            HeapAnalysisView.ClassMemoryUsage row = rows.get(i);
            double pct = totalBytes > 0 ? (row.shallowBytes() * 100.0) / totalBytes : 0;
            top.add(new HeapAnalysisView.ClassMemoryUsage(
                    i + 1,
                    row.className(),
                    row.instanceCount(),
                    row.shallowBytes(),
                    round1(pct)
            ));
        }
        return List.copyOf(top);
    }

    private static String cleanClassName(String value) {
        String name = value.trim();
        int moduleIdx = name.indexOf(" (");
        if (moduleIdx > 0) {
            name = name.substring(0, moduleIdx).trim();
        }
        return name;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
