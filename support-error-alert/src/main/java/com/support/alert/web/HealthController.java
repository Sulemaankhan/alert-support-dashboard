package com.support.alert.web;

import com.support.alert.health.ApmAlertsView;
import com.support.alert.health.ApmMetricsService;
import com.support.alert.health.ApmMetricsView;
import com.support.alert.health.ApmRealtimeHub;
import com.support.alert.health.ApmSnapshot;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final ApmMetricsService apmMetricsService;
    private final ApmRealtimeHub realtimeHub;

    public HealthController(ApmMetricsService apmMetricsService, ApmRealtimeHub realtimeHub) {
        this.apmMetricsService = apmMetricsService;
        this.realtimeHub = realtimeHub;
    }

    @GetMapping("/apm")
    public ApmSnapshot apm() {
        return realtimeHub.currentSnapshot();
    }

    @GetMapping("/apm/metrics")
    public ApmMetricsView metrics() {
        return realtimeHub.currentMetrics();
    }

    @GetMapping("/apm/alerts")
    public ApmAlertsView alerts() {
        return realtimeHub.currentAlerts();
    }

    @GetMapping("/apm/stack")
    public Map<String, Object> stack() {
        List<ApmSnapshot.ThreadStack> stacks = apmMetricsService.fullStackDump();
        return Map.of(
                "threadCount", stacks.size(),
                "threads", stacks
        );
    }

    @GetMapping(path = "/apm/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamApm() {
        return realtimeHub.subscribeApm();
    }

    @GetMapping(path = "/apm/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMetrics() {
        return realtimeHub.subscribeMetrics();
    }

    @GetMapping(path = "/apm/alerts/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAlerts() {
        return realtimeHub.subscribeAlerts();
    }
}
