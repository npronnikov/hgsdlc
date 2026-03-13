package ru.hgd.sdlc.auth.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
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
        Role seedRole = Role.valueOf(properties.getSeedRole());
        Instant now = Instant.now();

        List<SeedUser> seeds = List.of(
                new SeedUser(
                        properties.getSeedUsername(),
                        "All Roles",
                        seedRole,
                        properties.getSeedPassword()
                ),
                new SeedUser("flow_configurator", "Flow Configurator", Role.FLOW_CONFIGURATOR, "admin"),
                new SeedUser("product_owner", "Product Owner", Role.PRODUCT_OWNER, "admin"),
                new SeedUser("tech_approver", "Tech Approver", Role.TECH_APPROVER, "admin")
        );

        List<User> users = seeds.stream()
                .map(seed -> userRepository.findByUsername(seed.username)
                        .map(existing -> {
                            existing.setDisplayName(seed.displayName);
                            existing.setRole(seed.role);
                            existing.setPasswordHash(passwordEncoder.encode(seed.password));
                            existing.setEnabled(true);
                            if (existing.getCreatedAt() == null) {
                                existing.setCreatedAt(now);
                            }
                            return existing;
                        })
                        .orElseGet(() -> User.builder()
                                .id(UUID.randomUUID())
                                .username(seed.username)
                                .displayName(seed.displayName)
                                .role(seed.role)
                                .passwordHash(passwordEncoder.encode(seed.password))
                                .enabled(true)
                                .createdAt(now)
                                .build()))
                .toList();

        userRepository.saveAll(users);
        log.info("Seeded auth users: {}", users.stream().map(User::getUsername).toList());
    }

    private record SeedUser(String username, String displayName, Role role, String password) {}
}
