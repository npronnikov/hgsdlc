package ru.hgd.sdlc.auth.api;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.hgd.sdlc.auth.application.UserManagementService;
import ru.hgd.sdlc.auth.domain.Role;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.common.ForbiddenException;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;

@RestController
@RequestMapping("/api/admin/users")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> list() {
        return userManagementService.listAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody CreateUserRequest request) {
        Set<Role> roles = parseRoles(request.roles());
        return UserResponse.from(userManagementService.createUser(
                request.username(),
                request.displayName(),
                request.password(),
                roles
        ));
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(
            @PathVariable UUID userId,
            @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal User caller
    ) {
        Set<Role> roles = request.roles() != null ? parseRoles(request.roles()) : null;
        return UserResponse.from(userManagementService.updateUser(
                userId,
                caller.getUsername(),
                request.displayName(),
                roles,
                request.enabled()
        ));
    }

    @PutMapping("/{userId}/password")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @PathVariable UUID userId,
            @RequestBody ChangePasswordRequest request
    ) {
        userManagementService.changePassword(userId, request.password());
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID userId,
            @AuthenticationPrincipal User caller
    ) {
        userManagementService.deleteUser(userId, caller.getUsername());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<String> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<String> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<String> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }

    private Set<Role> parseRoles(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new ValidationException("At least one role is required");
        }
        Set<Role> roles = new HashSet<>();
        for (String name : roleNames) {
            try {
                roles.add(Role.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Unknown role: " + name);
            }
        }
        return roles;
    }
}
