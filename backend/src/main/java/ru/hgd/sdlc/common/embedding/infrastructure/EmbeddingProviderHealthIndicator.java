package ru.hgd.sdlc.common.embedding.infrastructure;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingProviderHealthIndicator implements HealthIndicator {

    private final EmbeddingProperties embeddingProperties;

    public EmbeddingProviderHealthIndicator(EmbeddingProperties embeddingProperties) {
        this.embeddingProperties = embeddingProperties;
    }

    @Override
    public Health health() {
        String provider = embeddingProperties.getProvider();

        if ("local".equals(provider)) {
            return Health.up()
                    .withDetail("provider", "local")
                    .withDetail("model", embeddingProperties.getLocal().getModelName())
                    .withDetail("dimension", embeddingProperties.getLocal().getDimension())
                    .build();
        } else if ("openai".equals(provider)) {
            if (embeddingProperties.getOpenai().getApiKey() == null ||
                embeddingProperties.getOpenai().getApiKey().isBlank()) {
                return Health.down()
                        .withDetail("provider", "openai")
                        .withDetail("error", "API key not configured")
                        .build();
            }
            return Health.up()
                    .withDetail("provider", "openai")
                    .withDetail("model", embeddingProperties.getOpenai().getModel())
                    .withDetail("dimension", embeddingProperties.getOpenai().getDimension())
                    .build();
        } else {
            return Health.down()
                    .withDetail("provider", provider)
                    .withDetail("error", "Unknown provider")
                    .build();
        }
    }
}
