package ru.hgd.sdlc.auth.api;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateUserRequest(
        @JsonProperty("username") String username,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("password") String password,
        @JsonProperty("roles") List<String> roles
) {}
