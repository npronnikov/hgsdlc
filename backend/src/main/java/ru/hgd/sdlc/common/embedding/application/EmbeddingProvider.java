package ru.hgd.sdlc.common.embedding.application;

public interface EmbeddingProvider {
    float[] generateEmbedding(String text);

    int getDimension();

    String getProviderName();
}
