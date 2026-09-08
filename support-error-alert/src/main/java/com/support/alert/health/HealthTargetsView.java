package com.support.alert.health;

import java.util.List;

/** Catalog of applications and environments the HealthCheck UI can select. */
public record HealthTargetsView(
        String defaultApplication,
        String defaultEnvironment,
        List<Application> applications
) {
    public record Application(
            String id,
            String name,
            List<Environment> environments
    ) {
    }

    public record Environment(
            String id,
            String label,
            String url,
            String service,
            String source
    ) {
    }
}
