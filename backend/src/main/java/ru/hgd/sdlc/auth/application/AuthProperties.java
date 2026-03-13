package ru.hgd.sdlc.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    private long sessionTtlSeconds = 86400;
    private String seedUsername = "admin";
    private String seedPassword = "admin";
    private String seedRole = "FLOW_CONFIGURATOR";

    public long getSessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    public void setSessionTtlSeconds(long sessionTtlSeconds) {
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    public String getSeedUsername() {
        return seedUsername;
    }

    public void setSeedUsername(String seedUsername) {
        this.seedUsername = seedUsername;
    }

    public String getSeedPassword() {
        return seedPassword;
    }

    public void setSeedPassword(String seedPassword) {
        this.seedPassword = seedPassword;
    }

    public String getSeedRole() {
        return seedRole;
    }

    public void setSeedRole(String seedRole) {
        this.seedRole = seedRole;
    }
}
