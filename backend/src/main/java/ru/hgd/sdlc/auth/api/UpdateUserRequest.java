package ru.hgd.sdlc.auth.api;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateUserRequest(
        @JsonProperty("display_name") String displayName,
        @JsonProperty("roles") List<String> roles,
        @JsonProperty("enabled") Boolean enabled
) {}
