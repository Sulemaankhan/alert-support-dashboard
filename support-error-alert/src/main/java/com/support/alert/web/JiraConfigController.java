package com.support.alert.web;

import com.support.alert.jira.JiraIntegrationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/jira")
public class JiraConfigController {

    private final JiraIntegrationProperties props;

    public JiraConfigController(JiraIntegrationProperties props) {
        this.props = props;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean configured = props.isRunnable();
        return Map.of(
                "enabled", props.isEnabled(),
                "configured", configured,
                "siteUrl", props.getSiteUrl() != null ? props.getSiteUrl().trim() : ""
        );
    }
}
