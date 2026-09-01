package com.support.alert.health;

import com.support.alert.auth.MailAuthSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emails APM alert metrics to the configured HealthCheck recipient.
 */
@Service
public class ApmAlertMailService {

    private static final Logger log = LoggerFactory.getLogger(ApmAlertMailService.class);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private final HealthCheckProperties properties;
    private final Environment environment;
    private final JavaMailSender mailSender;
    private final ConcurrentHashMap<String, Long> lastSentAtByCode = new ConcurrentHashMap<>();

    public ApmAlertMailService(
            HealthCheckProperties properties,
            Environment environment,
            @Autowired(required = false) JavaMailSender mailSender) {
        this.properties = properties;
        this.environment = environment;
        this.mailSender = mailSender;
    }

    /**
     * Sends one email when there are active conditions that have not been emailed
     * within the cooldown window.
     */
    public void notifyActiveAlerts(ApmSnapshot snapshot, List<ApmSnapshot.AlertEvent> active) {
        if (!properties.isAlertEmailConfigured() || snapshot == null) {
            return;
        }
        if (active == null || active.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(1, properties.getAlertEmailCooldownMinutes()) * 60_000L;
        List<ApmSnapshot.AlertEvent> due = new ArrayList<>();
        for (ApmSnapshot.AlertEvent alert : active) {
            String code = alert.code() != null ? alert.code() : "UNKNOWN";
            String key = cooldownKey(snapshot, code);
            Long last = lastSentAtByCode.get(key);
            if (last != null && now - last < cooldownMs) {
                continue;
            }
            due.add(alert);
        }
        if (due.isEmpty()) {
            return;
        }

        String to = properties.getAlertEmailTo().trim();
        String subject = buildSubject(snapshot, due);
        String body = buildBody(snapshot, due, active);

        if (!MailAuthSupport.isMailSenderReady(mailSender, environment)) {
            log.warn(
                    "APM alert email skipped (SMTP not configured). Would send to {}: {}\n{}",
                    to,
                    subject,
                    body);
            // Still mark cooldown so logs are not spammed every poll.
            for (ApmSnapshot.AlertEvent alert : due) {
                lastSentAtByCode.put(cooldownKey(snapshot, alert.code() != null ? alert.code() : "UNKNOWN"), now);
            }
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(resolveFrom());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            for (ApmSnapshot.AlertEvent alert : due) {
                lastSentAtByCode.put(cooldownKey(snapshot, alert.code() != null ? alert.code() : "UNKNOWN"), now);
            }
            log.info(
                    "APM alert metrics emailed to {} (app={} env={} service={}, status={}, alerts={})",
                    to,
                    snapshot.applicationId(),
                    snapshot.environment(),
                    snapshot.serviceName(),
                    snapshot.status(),
                    due.size());
        } catch (Exception ex) {
            log.error("Failed to email APM alert metrics to {}: {}", to, ex.getMessage());
        }
    }

    private String resolveFrom() {
        if (properties.getAlertEmailFrom() != null && !properties.getAlertEmailFrom().isBlank()) {
            return properties.getAlertEmailFrom().trim();
        }
        String username = environment.getProperty("spring.mail.username");
        if (username != null && !username.isBlank()) {
            return username.trim();
        }
        return "noreply@localhost";
    }

    private static String cooldownKey(ApmSnapshot snapshot, String code) {
        String app = snapshot.applicationId() != null ? snapshot.applicationId() : "";
        String env = snapshot.environment() != null ? snapshot.environment() : "";
        return app + "|" + env + "|" + code;
    }

    private static String buildSubject(ApmSnapshot snapshot, List<ApmSnapshot.AlertEvent> due) {
        String service = snapshot.serviceName() != null ? snapshot.serviceName() : "service";
        String env = snapshot.environment() != null && !snapshot.environment().isBlank()
                ? "/" + snapshot.environment()
                : "";
        String status = snapshot.status() != null ? snapshot.status() : "UNKNOWN";
        String code = due.get(0).code() != null ? due.get(0).code() : "ALERT";
        if (due.size() == 1) {
            return "[APM " + status + "] " + service + env + " · " + code;
        }
        return "[APM " + status + "] " + service + env + " · " + due.size() + " alerts (" + code + "…)";
    }

    private static String buildBody(
            ApmSnapshot snapshot,
            List<ApmSnapshot.AlertEvent> due,
            List<ApmSnapshot.AlertEvent> allActive) {
        StringBuilder sb = new StringBuilder();
        sb.append("HealthCheck APM alert metrics\n");
        sb.append("============================\n\n");
        sb.append("Time: ").append(TIME.format(Instant.now())).append('\n');
        sb.append("Service: ").append(nullToDash(snapshot.serviceName())).append('\n');
        sb.append("Application: ").append(nullToDash(snapshot.applicationName())).append('\n');
        sb.append("Environment: ").append(nullToDash(
                snapshot.environmentLabel() != null && !snapshot.environmentLabel().isBlank()
                        ? snapshot.environmentLabel()
                        : snapshot.environment())).append('\n');
        sb.append("Status: ").append(nullToDash(snapshot.status())).append('\n');
        sb.append("Target: ").append(nullToDash(snapshot.targetUrl())).append('\n');
        sb.append("Source: ").append(nullToDash(snapshot.source())).append('\n');
        sb.append("Uptime: ").append(formatUptime(snapshot.uptimeMs())).append('\n');
        sb.append('\n');

        sb.append("New / due alerts\n");
        sb.append("---------------\n");
        for (ApmSnapshot.AlertEvent alert : due) {
            sb.append("• [")
                    .append(alert.severity())
                    .append("] ")
                    .append(alert.code())
                    .append(" — ")
                    .append(alert.message())
                    .append('\n');
        }
        sb.append('\n');

        if (allActive != null && allActive.size() > due.size()) {
            sb.append("All active conditions\n");
            sb.append("--------------------\n");
            for (ApmSnapshot.AlertEvent alert : allActive) {
                sb.append("• [")
                        .append(alert.severity())
                        .append("] ")
                        .append(alert.code())
                        .append(" — ")
                        .append(alert.message())
                        .append('\n');
            }
            sb.append('\n');
        }

        sb.append("Live metrics\n");
        sb.append("------------\n");
        if (snapshot.requests() != null) {
            var r = snapshot.requests();
            sb.append(String.format(
                    Locale.US,
                    "Throughput: %.1f rpm (total %d, Δ %d)%n",
                    r.requestsPerMinute(),
                    r.totalRequests(),
                    r.requestsDelta()));
            sb.append(String.format(
                    Locale.US,
                    "Errors: %.2f%% (errors %d / total %d)%n",
                    r.errorRatePercent(),
                    r.errorRequests(),
                    r.totalRequests()));
            sb.append(String.format(Locale.US, "Avg response: %.1f ms%n", r.avgResponseTimeMs()));
        }
        if (snapshot.latency() != null) {
            sb.append(String.format(
                    Locale.US,
                    "Latency max: %.1f ms (Apdex T=%.0f ms)%n",
                    snapshot.latency().maxMs(),
                    snapshot.latency().apdexThresholdMs()));
        }
        if (snapshot.apdex() != null) {
            sb.append(String.format(
                    Locale.US,
                    "Apdex: %.2f (%s)%n",
                    snapshot.apdex().score(),
                    snapshot.apdex().rating()));
        }
        if (snapshot.gc() != null) {
            var gc = snapshot.gc();
            sb.append(String.format(
                    Locale.US,
                    "GC: %d collections (%d ms total) · Δ %d / %d ms%n",
                    gc.collectionCount(),
                    gc.collectionTimeMs(),
                    gc.collectionCountDelta(),
                    gc.collectionTimeMsDelta()));
            if (gc.collectors() != null && !gc.collectors().isEmpty()) {
                for (ApmSnapshot.GcCollectorStats collector : gc.collectors()) {
                    sb.append(String.format(
                            Locale.US,
                            "  · %s: %d collections, %d ms (Δ %d / %d ms)%n",
                            collector.name(),
                            collector.collectionCount(),
                            collector.collectionTimeMs(),
                            collector.collectionCountDelta(),
                            collector.collectionTimeMsDelta()));
                }
            }
        }
        if (snapshot.heap() != null) {
            sb.append(String.format(
                    Locale.US,
                    "Heap: %.1f%% used (%s / %s)%n",
                    snapshot.heap().usedPercent(),
                    formatBytes(snapshot.heap().usedBytes()),
                    formatBytes(snapshot.heap().maxBytes())));
        }
        if (snapshot.load() != null) {
            sb.append(String.format(
                    Locale.US,
                    "CPU: process %.1f%% · system %.1f%%%n",
                    snapshot.load().processCpuLoad(),
                    snapshot.load().systemCpuLoad()));
        }
        if (snapshot.threads() != null) {
            sb.append(String.format(
                    Locale.US,
                    "Threads: live %d · runnable %d · blocked %d%n",
                    snapshot.threads().live(),
                    snapshot.threads().runnable(),
                    snapshot.threads().blocked()));
        }
        if (snapshot.probes() != null) {
            sb.append("Probes: liveness=")
                    .append(snapshot.probes().liveness())
                    .append(" readiness=")
                    .append(snapshot.probes().readiness())
                    .append('\n');
        }
        sb.append('\n');

        if (snapshot.transactions() != null && !snapshot.transactions().isEmpty()) {
            sb.append("Top transactions\n");
            sb.append("----------------\n");
            int limit = Math.min(8, snapshot.transactions().size());
            for (int i = 0; i < limit; i++) {
                var tx = snapshot.transactions().get(i);
                sb.append(String.format(
                        Locale.US,
                        "%d. %s  count=%d  errors=%d (%.1f%%)  avg=%.1fms  max=%.1fms  apdex=%.2f%n",
                        i + 1,
                        tx.uri(),
                        tx.count(),
                        tx.errorCount(),
                        tx.errorRatePercent(),
                        tx.avgMs(),
                        tx.maxMs(),
                        tx.apdex()));
            }
            sb.append('\n');
        }

        sb.append("—\n");
        sb.append("Sent by jira-support-alert HealthCheck APM.\n");
        return sb.toString();
    }

    private static String formatUptime(long uptimeMs) {
        if (uptimeMs <= 0) {
            return "n/a";
        }
        long seconds = uptimeMs / 1000;
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3600;
        long mins = (seconds % 3600) / 60;
        if (days > 0) {
            return days + "d " + hours + "h " + mins + "m";
        }
        if (hours > 0) {
            return hours + "h " + mins + "m";
        }
        return mins + "m";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "n/a";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.US, "%.1f MB", mb);
        }
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
