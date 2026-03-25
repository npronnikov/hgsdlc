package ru.hgd.sdlc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HgdSdlcApplication {
    public static void main(String[] args) {
        SpringApplication.run(HgdSdlcApplication.class, args);
    }
}
