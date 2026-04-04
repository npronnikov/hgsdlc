package ru.hgd.sdlc.settings.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "catalog.seed")
public class CatalogSeedProperties {
    private String repoUrl = "";
    private String defaultBranch = "";

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl == null ? "" : repoUrl;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch == null ? "" : defaultBranch;
    }
}
