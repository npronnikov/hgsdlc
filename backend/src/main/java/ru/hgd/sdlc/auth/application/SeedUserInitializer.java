package ru.hgd.sdlc.auth.application;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.auth.domain.Role;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.auth.infrastructure.UserRepository;

@Component
@Slf4j
public class SeedUserInitializer implements ApplicationRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

    public SeedUserInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthProperties properties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = Instant.now();

        List<SeedUser> seeds = properties.getSeedUsers().stream()
                .map(this::toSeedUser)
                .toList();
        if (seeds.isEmpty()) {
            log.warn("No auth seed users configured (auth.seed-users is empty), skip seeding");
            return;
        }

        List<User> users = seeds.stream()
                .map(seed -> userRepository.findByUsername(seed.username())
                        .map(existing -> {
                            existing.setDisplayName(seed.displayName());
                            existing.setRole(seed.role());
                            existing.setRoles(seed.roles());
                            existing.setPasswordHash(passwordEncoder.encode(seed.password()));
                            existing.setEnabled(true);
                            if (existing.getCreatedAt() == null) {
                                existing.setCreatedAt(now);
                            }
                            return existing;
                        })
                        .orElseGet(() -> User.builder()
                                .id(UUID.randomUUID())
                                .username(seed.username())
                                .displayName(seed.displayName())
                                .role(seed.role())
                                .roles(seed.roles())
                                .passwordHash(passwordEncoder.encode(seed.password()))
                                .enabled(true)
                                .createdAt(now)
                                .build()))
                .toList();

        userRepository.saveAll(users);
        log.info("Seeded auth users: {}", users.stream().map(User::getUsername).toList());
    }

    private SeedUser toSeedUser(AuthProperties.SeedUser source) {
        if (source == null) {
            throw new ValidationException("auth.seed-users contains null item");
        }
        String username = normalizeUsername(source.getUsername());
        String displayName = normalizeDisplayName(source.getDisplayName(), username);
        String password = source.getPassword() == null ? "" : source.getPassword();
        if (password.isBlank()) {
            throw new ValidationException("auth.seed-users[" + username + "].password is required");
        }

        Set<Role> roles = parseRoles(source.getRoles());
        if (roles.isEmpty()) {
            Role singleRole = parseRole(source.getRole(), "auth.seed-users[" + username + "].role");
            roles = Set.of(singleRole);
        }
        Role primaryRole = resolvePrimaryRole(roles);
        return new SeedUser(username, displayName, primaryRole, roles, password);
    }

    private Set<Role> parseRoles(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return Set.of();
        }
        Set<Role> parsed = new LinkedHashSet<>();
        for (String roleName : roleNames) {
            if (roleName == null || roleName.isBlank()) {
                continue;
            }
            parsed.add(parseRole(roleName, "auth.seed-users[].roles"));
        }
        return parsed;
    }

    private Role parseRole(String roleName, String fieldPath) {
        if (roleName == null || roleName.isBlank()) {
            throw new ValidationException(fieldPath + " is required");
        }
        try {
            return Role.valueOf(roleName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Unknown role in " + fieldPath + ": " + roleName);
        }
    }

    private Role resolvePrimaryRole(Set<Role> roles) {
        if (roles.contains(Role.ADMIN)) {
            return Role.ADMIN;
        }
        return roles.stream()
                .min((left, right) -> left.name().compareTo(right.name()))
                .orElse(Role.FLOW_CONFIGURATOR);
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("auth.seed-users[].username is required");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayName(String displayName, String username) {
        if (displayName == null || displayName.isBlank()) {
            return username;
        }
        return displayName.trim();
    }

    private record SeedUser(String username, String displayName, Role role, Set<Role> roles, String password) {}
}
