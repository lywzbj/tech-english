-- Confirmed AI preview sheet per (user, article): key words + structural signposts.
CREATE TABLE reading_preview (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted INT NOT NULL DEFAULT 0,
    enable_status INT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    words_json TEXT,
    grammar_json TEXT,
    prompt_version VARCHAR(40),
    provider VARCHAR(30),
    model VARCHAR(80),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reading_preview_user_article (user_id, article_id),
    CONSTRAINT fk_reading_preview_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_reading_preview_article FOREIGN KEY (article_id) REFERENCES reading_article (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
