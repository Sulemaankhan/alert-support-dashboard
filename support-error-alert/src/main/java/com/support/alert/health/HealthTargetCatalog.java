package com.support.alert.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves configured HealthCheck applications and environments.
 */
@Component
public class HealthTargetCatalog {

    private final HealthCheckProperties properties;
    private final String localServiceName;

    public HealthTargetCatalog(
            HealthCheckProperties properties,
            @Value("${spring.application.name:support-error-alert}") String localServiceName) {
        this.properties = properties;
        this.localServiceName = localServiceName;
    }

    public HealthTargetsView view() {
        List<HealthTarget> targets = list();
        Map<String, List<HealthTarget>> byApp = new LinkedHashMap<>();
        for (HealthTarget target : targets) {
            byApp.computeIfAbsent(target.applicationId(), ignored -> new ArrayList<>()).add(target);
        }
        List<HealthTargetsView.Application> applications = new ArrayList<>();
        byApp.forEach((appId, envs) -> {
            String name = envs.get(0).applicationName();
            List<HealthTargetsView.Environment> environments = envs.stream()
                    .map(t -> new HealthTargetsView.Environment(
                            t.environmentId(),
                            t.environmentLabel(),
                            t.isRemote() ? t.normalizedActuatorBaseUrl() : "local",
                            t.resolvedServiceName(localServiceName),
                            t.isRemote() ? "remote" : "local"
                    ))
                    .toList();
            applications.add(new HealthTargetsView.Application(appId, name, environments));
        });
        HealthTarget defaults = defaultTarget();
        return new HealthTargetsView(
                defaults.applicationId(),
                defaults.environmentId(),
                List.copyOf(applications)
        );
    }

    public HealthTarget defaultTarget() {
        return resolve(properties.getDefaultApplication(), properties.getDefaultEnvironment());
    }

    public HealthTarget resolve(String applicationId, String environmentId) {
        List<HealthTarget> all = list();
        if (all.isEmpty()) {
            throw new IllegalArgumentException("No HealthCheck applications are configured.");
        }

        String requestedApp = blankToNull(applicationId);
        String requestedEnv = blankToNull(environmentId);
        if (requestedApp == null) {
            requestedApp = blankToNull(properties.getDefaultApplication());
        }

        if (requestedApp != null) {
            String appId = requestedApp;
            List<HealthTarget> forApp = all.stream()
                    .filter(t -> t.applicationId().equalsIgnoreCase(appId))
                    .toList();
            if (forApp.isEmpty()) {
                throw new IllegalArgumentException("Unknown application: " + appId);
            }
            String envId = requestedEnv != null
                    ? requestedEnv
                    : blankToNull(properties.getDefaultEnvironment());
            if (envId != null) {
                for (HealthTarget target : forApp) {
                    if (target.environmentId().equalsIgnoreCase(envId)) {
                        return target;
                    }
                }
                throw new IllegalArgumentException(
                        "Unknown environment '" + envId + "' for application '" + appId + "'");
            }
            return forApp.get(0);
        }

        String defEnv = blankToNull(properties.getDefaultEnvironment());
        if (defEnv != null) {
            for (HealthTarget target : all) {
                if (target.environmentId().equalsIgnoreCase(defEnv)) {
                    return target;
                }
            }
        }
        return all.get(0);
    }

    /**
     * One target per application: matching {@code preferredEnv} when present, else {@code local}, else first.
     */
    public List<HealthTarget> primaryTargets(String preferredEnv) {
        Map<String, List<HealthTarget>> byApp = new LinkedHashMap<>();
        for (HealthTarget target : list()) {
            byApp.computeIfAbsent(target.applicationId(), ignored -> new ArrayList<>()).add(target);
        }
        String wanted = blankToNull(preferredEnv);
        List<HealthTarget> selected = new ArrayList<>();
        byApp.forEach((appId, envs) -> selected.add(pickEnvironment(envs, wanted)));
        return List.copyOf(selected);
    }

    private static HealthTarget pickEnvironment(List<HealthTarget> envs, String wanted) {
        if (wanted != null) {
            for (HealthTarget target : envs) {
                if (target.environmentId().equalsIgnoreCase(wanted)) {
                    return target;
                }
            }
        }
        for (HealthTarget target : envs) {
            if ("local".equalsIgnoreCase(target.environmentId())) {
                return target;
            }
        }
        return envs.get(0);
    }

    public List<HealthTarget> list() {
        List<HealthTarget> out = new ArrayList<>();
        Map<String, HealthCheckProperties.ApplicationConfig> applications = properties.getApplications();
        if (applications != null && !applications.isEmpty()) {
            applications.forEach((appId, app) -> {
                if (appId == null || appId.isBlank() || app == null) {
                    return;
                }
                Map<String, HealthCheckProperties.EnvironmentConfig> environments = app.getEnvironments();
                if (environments == null || environments.isEmpty()) {
                    out.add(toTarget(appId.trim(), app, "local", new HealthCheckProperties.EnvironmentConfig()));
                    return;
                }
                environments.forEach((envId, env) -> {
                    if (envId == null || envId.isBlank() || env == null || !env.isEnabled()) {
                        return;
                    }
                    out.add(toTarget(appId.trim(), app, envId.trim(), env));
                });
            });
        }
        if (out.isEmpty() && properties.getUrl() != null && !properties.getUrl().isBlank()) {
            String appId = blankToNull(properties.getService()) != null
                    ? properties.getService().trim()
                    : "default";
            String envId = blankToNull(properties.getDefaultEnvironment()) != null
                    ? properties.getDefaultEnvironment().trim()
                    : "local";
            out.add(new HealthTarget(
                    appId,
                    titleCase(appId),
                    envId,
                    titleCase(envId),
                    properties.getUrl(),
                    properties.getService()
            ));
        }
        if (out.isEmpty()) {
            out.add(new HealthTarget(
                    localServiceName,
                    titleCase(localServiceName),
                    "local",
                    "Local",
                    "",
                    localServiceName
            ));
        }
        return List.copyOf(out);
    }

    private HealthTarget toTarget(
            String appId,
            HealthCheckProperties.ApplicationConfig app,
            String envId,
            HealthCheckProperties.EnvironmentConfig env) {
        String appName = blankToNull(app.getName()) != null ? app.getName().trim() : titleCase(appId);
        String envLabel = blankToNull(env.getLabel()) != null ? env.getLabel().trim() : titleCase(envId);
        String service = blankToNull(env.getService()) != null
                ? env.getService().trim()
                : (blankToNull(app.getName()) != null ? app.getName().trim() : appId);
        String url = env.getUrl() != null ? env.getUrl() : "";
        return new HealthTarget(appId, appName, envId, envLabel, url, service);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        String trimmed = value.trim();
        if (trimmed.length() == 1) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }
}
