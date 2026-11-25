-- Resume-reading bookmark: last scroll percentage per (user, article).
CREATE TABLE reading_progress (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted INT NOT NULL DEFAULT 0,
    enable_status INT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    scroll_percent INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reading_progress_user_article (user_id, article_id),
    CONSTRAINT fk_reading_progress_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_reading_progress_article FOREIGN KEY (article_id) REFERENCES reading_article (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
