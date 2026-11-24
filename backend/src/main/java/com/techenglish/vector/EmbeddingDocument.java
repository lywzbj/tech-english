package com.techenglish.vector;

import com.techenglish.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "embedding_document", uniqueConstraints = @UniqueConstraint(
        name = "uk_embedding_document_source",
        columnNames = {"source_type", "source_id", "chunk_index", "model"}))
public class EmbeddingDocument extends BaseEntity {

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String snippet;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(nullable = false)
    private int dimension;

    @Column(name = "vector_json", nullable = false, columnDefinition = "TEXT")
    private String vectorJson;
}
