package ru.hgd.sdlc.auth.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.auth.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    List<User> findAllByOrderByCreatedAtAsc();
    boolean existsByUsernameAndIdNot(String username, UUID id);
}
