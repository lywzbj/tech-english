CREATE TABLE embedding_document (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    title VARCHAR(240) NOT NULL,
    snippet TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    model VARCHAR(80) NOT NULL,
    dimension INT NOT NULL,
    vector_json TEXT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_embedding_document_source (source_type, source_id, chunk_index, model),
    KEY idx_embedding_document_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
