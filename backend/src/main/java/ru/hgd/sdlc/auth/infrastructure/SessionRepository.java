package ru.hgd.sdlc.auth.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.auth.domain.AuthSession;

public interface SessionRepository extends JpaRepository<AuthSession, UUID> {
    Optional<AuthSession> findByToken(String token);
    void deleteByToken(String token);
    void deleteByExpiresAtBefore(Instant now);
}
