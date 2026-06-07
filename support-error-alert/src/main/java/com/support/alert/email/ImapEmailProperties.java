package com.support.alert.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "support.email.imap")
public class ImapEmailProperties {

    /**
     * When false, inbox API returns status DISABLED without connecting.
     */
    private boolean enabled = false;

    private String host = "";
    private int port = 993;
    private String username = "";
    private String password = "";
    private String folder = "INBOX";
    private int maxResults = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }

    public boolean isRunnable() {
        return enabled
                && host != null
                && !host.isBlank()
                && username != null
                && !username.isBlank()
                && hasPassword();
    }
}
