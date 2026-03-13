package ru.hgd.sdlc.auth.application;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.AuthSession;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.auth.infrastructure.SessionRepository;
import ru.hgd.sdlc.auth.infrastructure.UserRepository;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

    public AuthService(
            UserRepository userRepository,
            SessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            AuthProperties properties
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public AuthSession login(String username, String password) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            log.warn("Login failed: user not found for username={}", username);
            throw new AuthException("Invalid credentials");
        }

        if (!user.isEnabled()) {
            log.warn("Login failed: user disabled for username={}", username);
            throw new AuthException("User is disabled");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Login failed: password mismatch for username={}", username);
            throw new AuthException("Invalid credentials");
        }

        Instant now = Instant.now();
        AuthSession session = AuthSession.builder()
                .id(UUID.randomUUID())
                .user(user)
                .token(UUID.randomUUID().toString())
                .createdAt(now)
                .expiresAt(now.plusSeconds(properties.getSessionTtlSeconds()))
                .build();

        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public User authenticate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        AuthSession session = sessionRepository.findByToken(token).orElse(null);
        if (session == null) {
            return null;
        }
        if (session.getExpiresAt().isBefore(Instant.now())) {
            return null;
        }
        return session.getUser();
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessionRepository.findByToken(token).ifPresent(sessionRepository::delete);
        sessionRepository.flush();
    }
}
