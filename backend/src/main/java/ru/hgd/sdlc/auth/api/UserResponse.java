package ru.hgd.sdlc.auth.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import ru.hgd.sdlc.auth.domain.Role;
import ru.hgd.sdlc.auth.domain.User;

public record UserResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("username") String username,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("roles") List<String> roles,
        @JsonProperty("enabled") boolean enabled,
        @JsonProperty("created_at") Instant createdAt
) {
    public static UserResponse from(User user) {
        List<String> roleNames = user.getEffectiveRoles().stream()
                .map(Role::name)
                .sorted()
                .toList();
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                roleNames,
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
