-- Manageable document-type tags for the reading lab.
-- source_type binds a tag to a legacy ReadingSourceType enum (NULL = purely user-defined tag).
CREATE TABLE reading_tag (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    enable_status INTEGER NOT NULL DEFAULT 0,
    name VARCHAR(40) NOT NULL,
    source_type VARCHAR(20),
    sort_order INTEGER NOT NULL DEFAULT 100,
    CONSTRAINT uk_reading_tag_name UNIQUE (name)
);

INSERT INTO reading_tag (created_at, updated_at, name, source_type, sort_order) VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '雅思学术', 'IELTS', 10),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '技术文档', 'TECH', 20),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '我的导入', 'CUSTOM', 30);

ALTER TABLE reading_article ADD COLUMN tag_id INTEGER;
CREATE INDEX idx_reading_article_tag ON reading_article (tag_id);

UPDATE reading_article SET tag_id = (
    SELECT t.id FROM reading_tag t WHERE t.source_type = reading_article.source_type LIMIT 1
) WHERE tag_id IS NULL;
