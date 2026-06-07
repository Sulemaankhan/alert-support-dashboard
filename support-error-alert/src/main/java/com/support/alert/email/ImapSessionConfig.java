package com.support.alert.email;

/**
 * IMAP connection settings for a single inbox search (from UI or optional server defaults).
 */
public final class ImapSessionConfig {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String folder;
    private final int maxResults;
    private final boolean oauth2;
    private final String oauth2AccessToken;

    public ImapSessionConfig(
            String host,
            int port,
            String username,
            String password,
            String folder,
            int maxResults) {
        this(host, port, username, password, folder, maxResults, false, null);
    }

    public ImapSessionConfig(
            String host,
            int port,
            String username,
            String folder,
            int maxResults,
            String oauth2AccessToken) {
        this(host, port, username, "", folder, maxResults, true, oauth2AccessToken);
    }

    private ImapSessionConfig(
            String host,
            int port,
            String username,
            String password,
            String folder,
            int maxResults,
            boolean oauth2,
            String oauth2AccessToken) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.folder = folder;
        this.maxResults = maxResults;
        this.oauth2 = oauth2;
        this.oauth2AccessToken = oauth2AccessToken;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getFolder() {
        return folder;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public boolean isOauth2() {
        return oauth2;
    }

    public String getOauth2AccessToken() {
        return oauth2AccessToken;
    }
}
