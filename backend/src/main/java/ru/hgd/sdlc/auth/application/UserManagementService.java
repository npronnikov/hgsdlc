package ru.hgd.sdlc.auth.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.Role;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.auth.infrastructure.UserRepository;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.common.ForbiddenException;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;

@Service
public class UserManagementService {

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<User> listAll() {
        return userRepository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public User createUser(String username, String displayName, String password, Set<Role> roles) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("Username is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new ValidationException("Display name is required");
        }
        if (password == null || password.isBlank()) {
            throw new ValidationException("Password is required");
        }
        if (password.length() < 6) {
            throw new ValidationException("Password must be at least 6 characters");
        }
        if (roles == null || roles.isEmpty()) {
            throw new ValidationException("At least one role is required");
        }

        String normalizedUsername = username.trim().toLowerCase();
        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new ConflictException("Username '" + normalizedUsername + "' is already taken");
        }

        User user = User.builder()
                .id(UUID.randomUUID())
                .username(normalizedUsername)
                .displayName(displayName.trim())
                .passwordHash(passwordEncoder.encode(password))
                .roles(new HashSet<>(roles))
                .role(syncLegacyRole(roles))
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        User saved = userRepository.save(user);
        log.info("User created: username={}, roles={}", saved.getUsername(), saved.getRoles());
        return saved;
    }

    @Transactional
    public User updateUser(UUID targetId, String callerUsername, String displayName, Set<Role> roles, Boolean enabled) {
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new NotFoundException("User not found: " + targetId));

        if (Boolean.FALSE.equals(enabled) && user.getUsername().equals(callerUsername)) {
            throw new ForbiddenException("You cannot disable your own account");
        }

        if (displayName != null) {
            if (displayName.isBlank()) {
                throw new ValidationException("Display name cannot be blank");
            }
            user.setDisplayName(displayName.trim());
        }

        if (roles != null) {
            if (roles.isEmpty()) {
                throw new ValidationException("At least one role is required");
            }
            user.setRoles(new HashSet<>(roles));
            user.setRole(syncLegacyRole(roles));
        }

        if (enabled != null) {
            user.setEnabled(enabled);
        }

        User saved = userRepository.save(user);
        log.info("User updated: id={}, username={}", saved.getId(), saved.getUsername());
        return saved;
    }

    @Transactional
    public void changePassword(UUID targetId, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new ValidationException("Password is required");
        }
        if (newPassword.length() < 6) {
            throw new ValidationException("Password must be at least 6 characters");
        }

        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new NotFoundException("User not found: " + targetId));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for user: id={}", targetId);
    }

    @Transactional
    public void deleteUser(UUID targetId, String callerUsername) {
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new NotFoundException("User not found: " + targetId));

        if (user.getUsername().equals(callerUsername)) {
            throw new ForbiddenException("You cannot delete your own account");
        }

        userRepository.delete(user);
        log.info("User deleted: id={}, username={}", targetId, user.getUsername());
    }

    private Role syncLegacyRole(Set<Role> roles) {
        if (roles.contains(Role.ADMIN)) {
            return Role.ADMIN;
        }
        return roles.stream()
                .min(Comparator.comparing(Role::name))
                .orElse(Role.FLOW_CONFIGURATOR);
    }
}
