package ru.hgd.sdlc.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChangePasswordRequest(
        @JsonProperty("password") String password
) {}
