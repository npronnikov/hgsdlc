package ru.hgd.sdlc.auth.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String username;

    @Column(nullable = false, length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private Role role;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 64)
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant createdAt;

    public void setRoles(Set<Role> roles) {
        this.roles = roles == null ? new HashSet<>() : new HashSet<>(roles);
    }

    public Set<Role> getEffectiveRoles() {
        if (roles != null && !roles.isEmpty()) {
            return Collections.unmodifiableSet(roles);
        }
        if (role != null) {
            return Set.of(role);
        }
        return Set.of();
    }

    public boolean hasRole(Role roleToCheck) {
        return roleToCheck != null && getEffectiveRoles().contains(roleToCheck);
    }

    public boolean hasAnyRole(Role... rolesToCheck) {
        if (rolesToCheck == null || rolesToCheck.length == 0) {
            return false;
        }
        for (Role roleToCheck : rolesToCheck) {
            if (hasRole(roleToCheck)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return false;
        }
        String normalized = roleName.trim().toUpperCase(Locale.ROOT);
        return getEffectiveRoles().stream()
                .map(Role::name)
                .anyMatch(normalized::equals);
    }

    public boolean hasAnyRoleName(Collection<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return false;
        }
        Set<String> normalized = roleNames.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (normalized.isEmpty()) {
            return false;
        }
        return getEffectiveRoles().stream()
                .map(Role::name)
                .anyMatch(normalized::contains);
    }
}
