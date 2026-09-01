package com.support.alert.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
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

    public RemoteActuatorClient(HealthCheckProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        Duration connect = properties.getConnectTimeout() != null ? properties.getConnectTimeout() : Duration.ofSeconds(2);
        Duration read = properties.getReadTimeout() != null ? properties.getReadTimeout() : Duration.ofSeconds(5);
        ClientHttpRequestFactory factory = ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(connect)
                        .withReadTimeout(read)
        );
        this.restClient = RestClient.builder().requestFactory(factory).build();
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
}
