package com.support.alert.health;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/** Lazily creates one {@link TargetApmSession} per application+environment. */
@Component
public class TargetApmRegistry {

    private final ConcurrentHashMap<String, TargetApmSession> sessions = new ConcurrentHashMap<>();
    private final HealthCheckProperties properties;
    private final RemoteActuatorClient actuatorClient;

    public TargetApmRegistry(HealthCheckProperties properties, RemoteActuatorClient actuatorClient) {
        this.properties = properties;
        this.actuatorClient = actuatorClient;
    }

    public TargetApmSession session(HealthTarget target) {
        return sessions.computeIfAbsent(target.key(), ignored -> new TargetApmSession(
                target,
                target.isRemote() ? new RemoteApmCollector(target, properties, actuatorClient) : null
        ));
    }

    public Collection<TargetApmSession> all() {
        return sessions.values();
    }
}
