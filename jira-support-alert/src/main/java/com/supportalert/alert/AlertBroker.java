package com.supportalert.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AlertBroker {

    private static final Logger log = LoggerFactory.getLogger(AlertBroker.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public AlertBroker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            log.warn("SSE connect failed", e);
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void publish(AlertEvent event) {
        String json;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", event.getType().name());
            payload.put("message", event.getMessage());
            payload.put("issueId", event.getIssueId());
            payload.put("detail", event.getDetail());
            payload.put("timestamp", event.getTimestamp().toString());
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Serialize alert", e);
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("alert").data(json));
            } catch (IOException ex) {
                emitters.remove(emitter);
            }
        }
    }
}
