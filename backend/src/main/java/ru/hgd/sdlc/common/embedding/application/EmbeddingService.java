package ru.hgd.sdlc.common.embedding.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int MAX_RETRIES = 3;

    private final EmbeddingProvider primaryProvider;

    public EmbeddingService(EmbeddingProvider primaryProvider) {
        this.primaryProvider = primaryProvider;
        log.info("Initialized EmbeddingService with provider: {}", primaryProvider.getProviderName());
    }

    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Attempted to generate embedding for null or blank text");
            return new float[primaryProvider.getDimension()];
        }

        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                float[] embedding = primaryProvider.generateEmbedding(text);
                if (embedding == null || embedding.length != primaryProvider.getDimension()) {
                    throw new IllegalStateException(
                        "Invalid embedding dimension: expected " + primaryProvider.getDimension() +
                        ", got " + (embedding == null ? "null" : embedding.length)
                    );
                }
                log.debug("Generated embedding for text (length: {}) using {} (attempt: {})",
                         text.length(), primaryProvider.getProviderName(), attempt + 1);
                return embedding;
            } catch (Exception e) {
                lastException = e;
                attempt++;
                if (attempt < MAX_RETRIES) {
                    log.warn("Attempt {} failed to generate embedding: {}", attempt, e.getMessage());
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry delay", ie);
                    }
                }
            }
        }

        log.error("Failed to generate embedding after {} attempts", MAX_RETRIES, lastException);
        return new float[primaryProvider.getDimension()];
    }

    public int getDimension() {
        return primaryProvider.getDimension();
    }

    public String getProviderName() {
        return primaryProvider.getProviderName();
    }
}
