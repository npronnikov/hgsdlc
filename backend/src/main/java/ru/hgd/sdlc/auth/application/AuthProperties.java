package ru.hgd.sdlc.auth.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    private long sessionTtlSeconds = 86400;
    private List<SeedUser> seedUsers = defaultSeedUsers();

    public long getSessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    public void setSessionTtlSeconds(long sessionTtlSeconds) {
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    public List<SeedUser> getSeedUsers() {
        return seedUsers;
    }

    public void setSeedUsers(List<SeedUser> seedUsers) {
        this.seedUsers = seedUsers == null ? new ArrayList<>() : new ArrayList<>(seedUsers);
    }

    private static List<SeedUser> defaultSeedUsers() {
        List<SeedUser> defaults = new ArrayList<>();
        defaults.add(seedUser(
                "admin",
                "All Roles",
                "admin",
                List.of("ADMIN", "FLOW_CONFIGURATOR", "PRODUCT_OWNER", "TECH_APPROVER")
        ));
        defaults.add(seedUser("flow_configurator", "Flow Configurator", "admin", List.of("FLOW_CONFIGURATOR")));
        defaults.add(seedUser("product_owner", "Product Owner", "admin", List.of("PRODUCT_OWNER")));
        defaults.add(seedUser("tech_approver", "Tech Approver", "admin", List.of("TECH_APPROVER")));
        return defaults;
    }

    private static SeedUser seedUser(String username, String displayName, String password, List<String> roles) {
        SeedUser user = new SeedUser();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPassword(password);
        user.setRoles(roles);
        return user;
    }

    public static class SeedUser {
        private String username;
        private String displayName;
        private String password;
        private String role;
        private List<String> roles = new ArrayList<>();

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
        }
    }
}
