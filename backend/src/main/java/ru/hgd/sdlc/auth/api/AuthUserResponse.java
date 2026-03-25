package ru.hgd.sdlc.auth.api;

import java.util.UUID;
import java.util.List;

public class AuthUserResponse {
    private UUID id;
    private String username;
    private String role;
    private List<String> roles;

    public AuthUserResponse() {
    }

    public AuthUserResponse(UUID id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.roles = List.of();
    }

    public AuthUserResponse(UUID id, String username, String role, List<String> roles) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.roles = roles;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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
        this.roles = roles;
    }
}
