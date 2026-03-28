package ru.hgd.sdlc.runtime.application.port;

import java.time.Instant;

public interface ClockPort {
    Instant now();
}
