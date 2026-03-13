package ru.hgd.sdlc.auth.api;

import java.util.UUID;

public class AuthUserResponse {
    private UUID id;
    private String username;
    private String role;

    public AuthUserResponse() {
    }

    public AuthUserResponse(UUID id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
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
}
