-- Manageable document-type tags for the reading lab.
-- source_type binds a tag to a legacy ReadingSourceType enum (NULL = purely user-defined tag).
CREATE TABLE reading_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted INT NOT NULL DEFAULT 0,
    enable_status INT NOT NULL DEFAULT 0,
    name VARCHAR(40) NOT NULL,
    source_type VARCHAR(20) NULL,
    sort_order INT NOT NULL DEFAULT 100,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reading_tag_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO reading_tag (created_at, updated_at, name, source_type, sort_order) VALUES
    (NOW(6), NOW(6), '雅思学术', 'IELTS', 10),
    (NOW(6), NOW(6), '技术文档', 'TECH', 20),
    (NOW(6), NOW(6), '我的导入', 'CUSTOM', 30);

ALTER TABLE reading_article ADD COLUMN tag_id BIGINT NULL AFTER source_type;
ALTER TABLE reading_article ADD KEY idx_reading_article_tag (tag_id);

UPDATE reading_article a
    JOIN reading_tag t ON t.source_type = a.source_type
SET a.tag_id = t.id
WHERE a.tag_id IS NULL;
