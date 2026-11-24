package com.techenglish.vector;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmbeddingDocumentRepository extends JpaRepository<EmbeddingDocument, Long> {

    Optional<EmbeddingDocument> findBySourceTypeAndSourceIdAndChunkIndexAndModel(
            String sourceType, Long sourceId, int chunkIndex, String model);

    List<EmbeddingDocument> findBySourceTypeAndSourceIdAndModelOrderByChunkIndexAsc(
            String sourceType, Long sourceId, String model);

    List<EmbeddingDocument> findByModelOrderByIdAsc(String model);

    long countByModel(String model);
}
