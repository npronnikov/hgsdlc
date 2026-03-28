package ru.hgd.sdlc.runtime.infrastructure;

import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.hgd.sdlc.runtime.application.port.ClockPort;

@Component
public class SystemClockAdapter implements ClockPort {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
