package com.support.alert.health;

import java.time.Instant;
import java.util.List;

/** Realtime alerts feed payload. */
public record ApmAlertsView(
        Instant timestamp,
        String serviceName,
        String status,
        int activeCount,
        List<ApmSnapshot.AlertEvent> active,
        List<ApmSnapshot.AlertEvent> alerts,
        List<ApmSnapshot.AlertEvent> latest
) {
}
