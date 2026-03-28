package ru.hgd.sdlc.runtime.infrastructure;

import org.springframework.stereotype.Component;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.runtime.application.port.IdentityPort;

@Component
public class DefaultIdentityAdapter implements IdentityPort {
    @Override
    public String resolveActorId(User user) {
        return user == null ? "system" : user.getUsername();
    }
}
