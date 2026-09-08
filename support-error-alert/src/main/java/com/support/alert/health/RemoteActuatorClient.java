package com.support.alert.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

@Component
public class RemoteActuatorClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteActuatorClient.class);

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RemoteActuatorClient(
            HealthCheckProperties properties,
            ObjectMapper objectMapper,
            ObservationRegistry observationRegistry) {
        this.objectMapper = objectMapper;
        Duration connect = properties.getConnectTimeout() != null ? properties.getConnectTimeout() : Duration.ofSeconds(2);
        Duration read = properties.getReadTimeout() != null ? properties.getReadTimeout() : Duration.ofSeconds(5);
        ClientHttpRequestFactory factory = ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(connect)
                        .withReadTimeout(read)
        );
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .observationRegistry(observationRegistry)
                .observationConvention(new ActuatorClientObservationConvention())
                .build();
    }

    public Optional<JsonNode> getJson(String actuatorBaseUrl, String relativePath) {
        String base = HealthCheckProperties.normalizeActuatorUrl(actuatorBaseUrl);
        if (base.isBlank()) {
            return Optional.empty();
        }
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        String url = base + path;
        try {
            String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readTree(body));
        } catch (RestClientException | java.io.IOException ex) {
            log.debug("Remote actuator GET {} failed: {}", url, ex.toString());
            return Optional.empty();
        }
    }

    /**
     * Keeps {@code http.client.requests} cardinality low while still naming the remote host.
     */
    static final class ActuatorClientObservationConvention extends DefaultClientRequestObservationConvention {
        @Override
        public String getName() {
            return "http.client.requests";
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(ClientRequestObservationContext context) {
            java.util.List<KeyValue> kept = new java.util.ArrayList<>();
            super.getLowCardinalityKeyValues(context).forEach(kv -> {
                if (!"uri".equals(kv.getKey()) && !"client.name".equals(kv.getKey())) {
                    kept.add(kv);
                }
            });
            return KeyValues.of(kept.toArray(KeyValue[]::new))
                    .and("client.name", hostLabel(context))
                    .and("uri", normalizedUri(context));
        }

        private static String hostLabel(ClientRequestObservationContext context) {
            try {
                URI uri = context.getCarrier() != null ? context.getCarrier().getURI() : null;
                if (uri == null || uri.getHost() == null) {
                    return "actuator";
                }
                return uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
            } catch (Exception ex) {
                return "actuator";
            }
        }

        private static String normalizedUri(ClientRequestObservationContext context) {
            try {
                URI uri = context.getCarrier() != null ? context.getCarrier().getURI() : null;
                if (uri == null || uri.getPath() == null) {
                    return "/actuator/**";
                }
                String path = uri.getPath();
                if (path.contains("/metrics")) {
                    return "/actuator/metrics";
                }
                if (path.contains("/health")) {
                    return "/actuator/health";
                }
                if (path.contains("/threaddump")) {
                    return "/actuator/threaddump";
                }
                if (path.contains("/info")) {
                    return "/actuator/info";
                }
                return "/actuator/**";
            } catch (Exception ex) {
                return "/actuator/**";
            }
        }
    }
}
