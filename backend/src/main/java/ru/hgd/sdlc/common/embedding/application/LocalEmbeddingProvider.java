package ru.hgd.sdlc.common.embedding.application;

import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class LocalEmbeddingProvider implements EmbeddingProvider {

    private static final int DIMENSION = 384;
    private final RandomGenerator random = RandomGenerator.getDefault();

    @Override
    public float[] generateEmbedding(String text) {
        // TODO: Replace with actual Sentence-BERT model inference
        // This is a placeholder implementation that generates pseudo-random vectors
        float[] embedding = new float[DIMENSION];
        for (int i = 0; i < DIMENSION; i++) {
            embedding[i] = random.nextFloat(-1.0f, 1.0f);
        }
        return embedding;
    }

    @Override
    public int getDimension() {
        return DIMENSION;
    }

    @Override
    public String getProviderName() {
        return "LOCAL_SBERT";
    }
}
