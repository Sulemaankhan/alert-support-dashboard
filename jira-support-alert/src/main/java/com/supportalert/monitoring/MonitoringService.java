package com.supportalert.monitoring;

import com.supportalert.issue.IssueService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MonitoringService {

    private final HealthEndpoint healthEndpoint;
    private final IssueService issueService;

    @Value("${app.monitoring.interval-ms}")
    private long intervalMs;

    public MonitoringService(HealthEndpoint healthEndpoint, IssueService issueService) {
        this.healthEndpoint = healthEndpoint;
        this.issueService = issueService;
    }

    public Map<String, Object> snapshot() {
        Status status = healthEndpoint.health().getStatus();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appStatus", status.getCode());
        body.put("openIssues", issueService.openCount());
        body.put("totalIssues", issueService.list().size());
        body.put("checkIntervalMs", intervalMs);
        body.put("timestamp", java.time.Instant.now().toString());
        return body;
    }
}
