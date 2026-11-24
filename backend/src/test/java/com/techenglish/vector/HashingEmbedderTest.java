package com.techenglish.vector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashingEmbedderTest {

    @Test
    void producesDeterministicNormalizedVectors() {
        float[] first = HashingEmbedder.embed("A database index speeds up queries", 384);
        float[] second = HashingEmbedder.embed("A database index speeds up queries", 384);

        assertThat(first).hasSize(384).containsExactly(second);
        double norm = 0;
        for (float value : first) {
            norm += value * value;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void relatedTextScoresHigherThanUnrelatedText() {
        float[] query = HashingEmbedder.embed("database index", 384);
        float[] related = HashingEmbedder.embed(
                "A database index stores sorted columns so the database can find rows quickly.", 384);
        float[] unrelated = HashingEmbedder.embed(
                "The weather was warm and the team walked along the river after lunch.", 384);

        assertThat(HashingEmbedder.cosine(query, related))
                .isGreaterThan(HashingEmbedder.cosine(query, unrelated));
    }

    @Test
    void blankTextProducesZeroVector() {
        float[] vector = HashingEmbedder.embed("   ", 64);

        assertThat(vector).hasSize(64).containsOnly(0f);
    }
}
