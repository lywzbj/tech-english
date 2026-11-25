-- Resume-reading bookmark: last scroll percentage per (user, article).
CREATE TABLE reading_progress (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    enable_status INTEGER NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    scroll_percent INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_reading_progress_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_reading_progress_article FOREIGN KEY (article_id) REFERENCES reading_article (id)
);
CREATE UNIQUE INDEX uk_reading_progress_user_article ON reading_progress (user_id, article_id);
