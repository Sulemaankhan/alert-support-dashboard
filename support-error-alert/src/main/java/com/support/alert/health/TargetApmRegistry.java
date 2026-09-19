package com.support.alert.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/** Lazily creates one {@link TargetApmSession} per application+environment. */
@Component
public class TargetApmRegistry {

    private final ConcurrentHashMap<String, TargetApmSession> sessions = new ConcurrentHashMap<>();
    private final HealthCheckProperties properties;
    private final RemoteActuatorClient actuatorClient;
    private final int serverPort;

    public TargetApmRegistry(
            HealthCheckProperties properties,
            RemoteActuatorClient actuatorClient,
            @Value("${server.port:8081}") int serverPort) {
        this.properties = properties;
        this.actuatorClient = actuatorClient;
        this.serverPort = serverPort;
    }

    public TargetApmSession session(HealthTarget target) {
        return sessions.computeIfAbsent(target.key(), ignored -> new TargetApmSession(
                target,
                target.usesLocalJvm(serverPort) ? null : new RemoteApmCollector(target, properties, actuatorClient)
        ));
    }

    public Collection<TargetApmSession> all() {
        return sessions.values();
    }
}
