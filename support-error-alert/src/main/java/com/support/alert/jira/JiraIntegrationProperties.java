package com.support.alert.jira;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "support.jira")
public class JiraIntegrationProperties {

    private boolean enabled = false;
    /** e.g. https://your-domain.atlassian.net (no trailing slash) */
    private String siteUrl = "";
    private String email = "";
    private String apiToken = "";
    private String projectKey = "";
    /** Issue type name in the project, e.g. Task or Bug */
    private String issueType = "Task";
    /** When false, the create payload omits priority (some projects do not use it). */
    private boolean setPriority = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public void setSiteUrl(String siteUrl) {
        this.siteUrl = siteUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public boolean isSetPriority() {
        return setPriority;
    }

    public void setSetPriority(boolean setPriority) {
        this.setPriority = setPriority;
    }

    public boolean isRunnable() {
        return enabled
                && notBlank(siteUrl)
                && notBlank(email)
                && notBlank(apiToken)
                && notBlank(projectKey);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
