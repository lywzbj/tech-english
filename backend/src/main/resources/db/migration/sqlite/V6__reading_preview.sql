-- Confirmed AI preview sheet per (user, article): key words + structural signposts.
CREATE TABLE reading_preview (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    enable_status INTEGER NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    words_json TEXT,
    grammar_json TEXT,
    prompt_version VARCHAR(40),
    provider VARCHAR(30),
    model VARCHAR(80),
    CONSTRAINT fk_reading_preview_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_reading_preview_article FOREIGN KEY (article_id) REFERENCES reading_article (id)
);
CREATE UNIQUE INDEX uk_reading_preview_user_article ON reading_preview (user_id, article_id);
