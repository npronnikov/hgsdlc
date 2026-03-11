package ru.hgd.sdlc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SdlcApplicationTests {

    @Test
    void contextLoads() {
        // Verify Spring context loads successfully with Testcontainers PostgreSQL
    }
}
