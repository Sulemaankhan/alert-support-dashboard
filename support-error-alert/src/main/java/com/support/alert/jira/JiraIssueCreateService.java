package com.support.alert.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.support.alert.model.AlertRecord;
import com.support.alert.model.AlertSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.Optional;

@Service
public class JiraIssueCreateService {

    private static final Logger log = LoggerFactory.getLogger(JiraIssueCreateService.class);

    private final JiraIntegrationProperties props;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RestClient> jiraRestClient;

    public JiraIssueCreateService(
            JiraIntegrationProperties props,
            ObjectMapper objectMapper,
            ObjectProvider<RestClient> jiraRestClient) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.jiraRestClient = jiraRestClient;
    }

    /**
     * Creates a Jira issue from the alert: description = error details, priority from severity.
     *
     * @param overrides optional {@code summary} (otherwise derived from error details)
     */
    public CreatedJiraIssue createIssue(AlertRecord alert, Map<String, String> overrides) {
        if (!props.isEnabled()) {
            throw new IllegalArgumentException("Jira integration is disabled. Set support.jira.enabled=true.");
        }
        if (!props.isRunnable()) {
            throw new IllegalArgumentException(
                    "Jira is not fully configured. Set support.jira.site-url, email, api-token, and project-key.");
        }

        RestClient client = jiraRestClient.getIfAvailable();
        if (client == null) {
            throw new IllegalArgumentException(
                    "Jira REST client is not available. Ensure support.jira.enabled=true and all required "
                            + "support.jira.* properties are set.");
        }

        ObjectNode body = buildIssuePayload(alert, overrides);
        try {
            String raw = client.post()
                    .uri("/rest/api/3/issue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            String key = root.path("key").asText(null);
            String self = root.path("self").asText(null);
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalStateException("Jira response missing issue key: " + raw);
            }
            log.info("Created Jira issue {} for alert {}", key, alert.getId());
            return new CreatedJiraIssue(key, self != null ? self : "");
        } catch (RestClientResponseException ex) {
            //String detail = ex.getResponseBodyAsString(StandardCharsets.UTF_8);
            String detail = "Test";
            log.warn("Jira create failed: {} — {}", ex.getStatusCode(), detail);
            throw new IllegalStateException(
                    "Jira API error (" + ex.getStatusCode().value() + "): "
                            + abbreviate(detail, 800),
                    ex);
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException) {
                throw null;
            }
            log.warn("Jira create failed", ex);
            throw new IllegalStateException("Jira request failed: " + ex.getMessage(), ex);
        }
    }

    private ObjectNode buildIssuePayload(AlertRecord alert, Map<String, String> overrides) {
        String summary = Optional.ofNullable(overrides)
                .map(o -> o.get("summary"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElseGet(() -> summarize(alert.getErrorDetails()));

        ObjectNode fields = objectMapper.createObjectNode();
        fields.set("project", objectMapper.createObjectNode().put("key", props.getProjectKey().trim()));
        fields.put("summary", summary);
        fields.set("description", JiraAdfDescription.fromPlainText(objectMapper, buildDescription(alert)));
        fields.set("issuetype", objectMapper.createObjectNode().put("name", props.getIssueType().trim()));

        if (props.isSetPriority()) {
            fields.set("priority", objectMapper.createObjectNode().put("name", priorityForSeverity(alert.getSeverity())));
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.set("fields", fields);
        return root;
    }

    private static String buildDescription(AlertRecord alert) {
        StringBuilder sb = new StringBuilder();
        if (alert.getErrorDetails() != null && !alert.getErrorDetails().trim().isEmpty()) {
            sb.append(alert.getErrorDetails().trim());
        } else {
            sb.append("(No error details)");
        }
        appendLine(sb, "Service", alert.getServiceName());
        appendLine(sb, "Function", alert.getFunctionPath());
        appendLine(sb, "File", alert.getFilePath());
        appendLine(sb, "Severity", alert.getSeverity() != null ? alert.getSeverity().name() : "");
        return sb.toString();
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        sb.append("\n\n").append(label).append(": ").append(value.trim());
    }

    private static String summarize(String errorDetails) {
        String s = errorDetails == null ? "" : errorDetails.trim();
        if (s.isEmpty()) {
            return "Support alert";
        }
        int nl = s.indexOf('\n');
        String first = nl >= 0 ? s.substring(0, nl) : s;
        if (first.length() > 240) {
            return first.substring(0, 237) + "…";
        }
        return first;
    }

    private static String priorityForSeverity(AlertSeverity severity) {
        if (severity == null) {
            return "Medium";
        }
        switch (severity) {
            case CRITICAL:
                return "Highest";
            case HIGH:
                return "High";
            case MEDIUM:
                return "Medium";
            case LOW:
                return "Low";
            case INFO:
                return "Lowest";
            default:
                return "Medium";
        }
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    public static final class CreatedJiraIssue {
        private final String issueKey;
        private final String issueSelf;

        public CreatedJiraIssue(String issueKey, String issueSelf) {
            this.issueKey = issueKey;
            this.issueSelf = issueSelf;
        }

        public String getIssueKey() {
            return issueKey;
        }

        public String getIssueSelf() {
            return issueSelf;
        }
    }
}
