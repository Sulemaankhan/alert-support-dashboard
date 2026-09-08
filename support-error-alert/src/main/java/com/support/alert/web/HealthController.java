package com.support.alert.web;

import com.support.alert.health.ApmAlertsView;
import com.support.alert.health.ApmMetricsService;
import com.support.alert.health.ApmMetricsView;
import com.support.alert.health.ApmRealtimeHub;
import com.support.alert.health.ApmSnapshot;
import com.support.alert.health.HealthTarget;
import com.support.alert.health.HealthTargetCatalog;
import com.support.alert.health.HealthTargetsView;
import com.support.alert.health.HeapAnalysisService;
import com.support.alert.health.HeapAnalysisView;
import com.support.alert.health.ServiceHealthBoard;
import com.support.alert.health.ServiceHealthService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final ApmMetricsService apmMetricsService;
    private final ApmRealtimeHub realtimeHub;
    private final HealthTargetCatalog targetCatalog;
    private final HeapAnalysisService heapAnalysisService;
    private final ServiceHealthService serviceHealthService;

    public HealthController(
            ApmMetricsService apmMetricsService,
            ApmRealtimeHub realtimeHub,
            HealthTargetCatalog targetCatalog,
            HeapAnalysisService heapAnalysisService,
            ServiceHealthService serviceHealthService) {
        this.apmMetricsService = apmMetricsService;
        this.realtimeHub = realtimeHub;
        this.targetCatalog = targetCatalog;
        this.heapAnalysisService = heapAnalysisService;
        this.serviceHealthService = serviceHealthService;
    }

    @GetMapping("/targets")
    public HealthTargetsView targets() {
        return targetCatalog.view();
    }

    @GetMapping("/apm")
    public ApmSnapshot apm(
            @RequestParam(required = false) String application,
            @RequestParam(required = false) String env) {
        return realtimeHub.currentSnapshot(resolve(application, env));
    }

    @GetMapping("/apm/metrics")
    public ApmMetricsView metrics(
            @RequestParam(required = false) String application,
            @RequestParam(required = false) String env) {
        return realtimeHub.currentMetrics(resolve(application, env));
    }

    @GetMapping("/apm/alerts")
    public ApmAlertsView alerts(
            @RequestParam(required = false) String application,
            @RequestParam(required = false) String env) {
        return realtimeHub.currentAlerts(resolve(application, env));
    }

    @GetMapping("/apm/heap-analysis")
    public HeapAnalysisView heapAnalysis(
            @RequestParam(required = false) String application,
            @RequestParam(required = false) String env) {
        HealthTarget target = resolve(application, env);
        return heapAnalysisService.analyze(target);
    }

    @GetMapping("/apm/services")
    public ServiceHealthBoard services(@RequestParam(required = false) String env) {
        return serviceHealthService.board(env);
    }

    @GetMapping("/apm/stack")
    public Map<String, Object> stack(
            @RequestParam(required = false) String application,
            @RequestParam(required = false) String env) {
        HealthTarget target = resolve(application, env);
        List<ApmSnapshot.ThreadStack> stacks = apmMetricsService.fullStackDump(target);
        return Map.of(
                "threadCount", stacks.size(),
                "threads", stacks,
                "applicationId", target.applicationId(),
                "environment", target.environmentId()
        );
    }

    @GetMapping(path = "/apm/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamApm(
            @RequestParam(required = false) String application,
            @RequestParam(required = false) String env) {
        return realtimeHub.subscribeApm(resolve(application, env));
    }

    @GetMapping(path = "/apm/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMetrics(
            @RequestParam(required = false) String application,
            @RequestParam(required = false) String env) {
        return realtimeHub.subscribeMetrics(resolve(application, env));
    }

    @GetMapping(path = "/apm/alerts/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAlerts(
            @RequestParam(required = false) String application,
            @RequestParam(required = false) String env) {
        return realtimeHub.subscribeAlerts(resolve(application, env));
    }

    private HealthTarget resolve(String application, String env) {
        return targetCatalog.resolve(application, env);
    }
}
