package com.techenglish.vector;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final VectorProperties properties;
    private final OpenAiEmbeddingProvider remoteProvider;

    public boolean remote() {
        return "openai".equalsIgnoreCase(properties.getProvider());
    }

    public String providerName() {
        return remote() ? "openai-compatible" : "hash";
    }

    public String modelName() {
        return remote() ? properties.getModel() : "local-hash-" + dimension();
    }

    public int dimension() {
        return Math.max(32, properties.getDimension());
    }

    public boolean configured() {
        return !remote() || remoteProvider.configured();
    }

    public float[] embed(String text) {
        float[] vector = remote()
                ? remoteProvider.embed(text)
                : HashingEmbedder.embed(text, dimension());
        return normalizeToDimension(vector);
    }

    private float[] normalizeToDimension(float[] vector) {
        int dimension = dimension();
        float[] fitted = new float[dimension];
        System.arraycopy(vector, 0, fitted, 0, Math.min(vector.length, dimension));
        return HashingEmbedder.normalize(fitted);
    }
}
