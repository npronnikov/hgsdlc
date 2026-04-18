package ru.hgd.sdlc.common.embedding.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    @Configuration
    @ConditionalOnProperty(name = "embedding.provider", havingValue = "local")
    public static class LocalEmbeddingProviderConfig {
        // Local embedding provider beans will be defined here
    }

    @Configuration
    @ConditionalOnProperty(name = "embedding.provider", havingValue = "openai")
    public static class OpenAIEmbeddingProviderConfig {
        // OpenAI embedding provider beans will be defined here
    }
}
