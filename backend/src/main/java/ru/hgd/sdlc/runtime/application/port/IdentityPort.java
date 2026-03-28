package ru.hgd.sdlc.runtime.application.port;

import ru.hgd.sdlc.auth.domain.User;

public interface IdentityPort {
    String resolveActorId(User user);
}
