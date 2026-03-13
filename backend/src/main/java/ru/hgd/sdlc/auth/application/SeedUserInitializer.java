package ru.hgd.sdlc.auth.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.Role;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.auth.infrastructure.UserRepository;

@Component
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
        if (userRepository.count() > 0) {
            return;
        }

        Role role = Role.valueOf(properties.getSeedRole());
        User user = User.builder()
                .id(UUID.randomUUID())
                .username(properties.getSeedUsername())
                .displayName(properties.getSeedUsername())
                .role(role)
                .passwordHash(passwordEncoder.encode(properties.getSeedPassword()))
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        userRepository.save(user);
    }
}
