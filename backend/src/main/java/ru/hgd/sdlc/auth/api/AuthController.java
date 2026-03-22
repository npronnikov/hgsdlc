package ru.hgd.sdlc.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hgd.sdlc.auth.application.AuthException;
import ru.hgd.sdlc.auth.application.AuthService;
import ru.hgd.sdlc.auth.domain.AuthSession;
import ru.hgd.sdlc.auth.domain.Role;
import ru.hgd.sdlc.auth.domain.User;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AuthSession session = authService.login(request.getUsername(), request.getPassword());
        return new AuthResponse(session.getToken(), toUserResponse(session.getUser()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        String token = resolveToken(httpRequest);
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthUserResponse me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new AuthException("Unauthorized");
        }
        return toUserResponse(user);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<String> handleAuthException(AuthException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length());
    }

    private AuthUserResponse toUserResponse(User user) {
        List<String> roleNames = user.getEffectiveRoles().stream()
                .map(Role::name)
                .sorted(Comparator.naturalOrder())
                .toList();
        String primaryRole = roleNames.contains(Role.ADMIN.name())
                ? Role.ADMIN.name()
                : (user.getRole() == null ? (roleNames.isEmpty() ? null : roleNames.get(0)) : user.getRole().name());
        return new AuthUserResponse(user.getId(), user.getUsername(), primaryRole, roleNames);
    }
}
