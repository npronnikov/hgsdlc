package ru.hgd.sdlc.common.embedding.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "embedding.provider", havingValue = "openai")
public class OpenAIEmbeddingProvider implements EmbeddingProvider {

    private static final int DIMENSION = 1536;

    @Override
    public float[] generateEmbedding(String text) {
        // TODO: Replace with actual OpenAI API call
        // This is a placeholder implementation
        throw new UnsupportedOperationException(
            "OpenAI embedding provider is not yet implemented. " +
            "Please configure embedding.provider=local for local Sentence-BERT or implement OpenAI integration."
        );
    }

    @Override
    public int getDimension() {
        return DIMENSION;
    }

    @Override
    public String getProviderName() {
        return "OPENAI_ADA002";
    }
}
