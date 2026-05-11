package com.supportalert.alert;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class AlertStreamController {

    private final AlertBroker broker;

    public AlertStreamController(AlertBroker broker) {
        this.broker = broker;
    }

    @GetMapping(value = "/alerts/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return broker.subscribe();
    }
}
