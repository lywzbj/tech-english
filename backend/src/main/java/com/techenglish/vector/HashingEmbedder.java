package com.techenglish.vector;

import java.util.Locale;
import java.util.Set;

/**
 * Deterministic offline embedding: hashed unigrams and bigrams with L2 normalization.
 * It captures lexical overlap, not deep semantics. It keeps search working with no
 * API key; configure EMBEDDING_PROVIDER=openai for real semantic vectors.
 */
public final class HashingEmbedder {

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from",
            "has", "have", "he", "her", "his", "i", "in", "is", "it", "its", "of",
            "on", "or", "that", "the", "their", "them", "they", "this", "to", "was",
            "were", "will", "with", "you", "your"
    );

    private HashingEmbedder() {
    }

    public static float[] embed(String text, int dimension) {
        float[] vector = new float[Math.max(1, dimension)];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String[] tokens = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        String previous = null;
        for (String token : tokens) {
            if (token.length() < 2 || STOPWORDS.contains(token)) {
                continue;
            }
            add(vector, token, 1.0f);
            if (previous != null) {
                add(vector, previous + " " + token, 0.5f);
            }
            previous = token;
        }
        return normalize(vector);
    }

    private static void add(float[] vector, String term, float weight) {
        vector[Math.floorMod(fnv1a(term), vector.length)] += weight;
    }

    private static long fnv1a(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash == Long.MIN_VALUE ? 0 : hash;
    }

    public static float[] normalize(float[] vector) {
        double norm = 0;
        for (float value : vector) {
            norm += (double) value * value;
        }
        if (norm == 0) {
            return vector;
        }
        double scale = 1.0 / Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] * scale);
        }
        return vector;
    }

    public static double cosine(float[] left, float[] right) {
        int length = Math.min(left.length, right.length);
        double dot = 0;
        for (int i = 0; i < length; i++) {
            dot += (double) left[i] * right[i];
        }
        return dot;
    }
}
