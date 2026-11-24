package com.techenglish.vector;

import java.util.List;

public final class VectorDtos {

    private VectorDtos() {
    }

    public record SearchResult(
            String sourceType,
            Long sourceId,
            String title,
            String snippet,
            double score
    ) {
    }

    public record SearchResponse(
            String query,
            String mode,
            String provider,
            String model,
            int dimension,
            boolean sqliteVec,
            List<SearchResult> results
    ) {
    }

    public record VectorStatus(
            boolean enabled,
            String provider,
            String model,
            int dimension,
            long documents,
            String index,
            boolean configured
    ) {
    }
}
