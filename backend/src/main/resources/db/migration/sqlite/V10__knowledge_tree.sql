-- 语法知识树：前置依赖从 grammar_topic.prerequisites(JSON 文本) 升级为正式边表。
-- up_topic 依赖 down_topic（down 是前置/父节点）。JSON 列只保留为写入镜像，读取端一律走边表。
CREATE TABLE grammar_topic_edge (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    enable_status INTEGER NOT NULL DEFAULT 0,
    up_topic_id BIGINT NOT NULL,
    down_topic_id BIGINT NOT NULL,
    edge_type VARCHAR(20) NOT NULL DEFAULT 'PREREQ',
    CONSTRAINT fk_topic_edge_up FOREIGN KEY (up_topic_id) REFERENCES grammar_topic (id),
    CONSTRAINT fk_topic_edge_down FOREIGN KEY (down_topic_id) REFERENCES grammar_topic (id)
);
CREATE UNIQUE INDEX uk_topic_edge ON grammar_topic_edge (up_topic_id, down_topic_id);
CREATE INDEX idx_topic_edge_down ON grammar_topic_edge (down_topic_id);
CREATE INDEX idx_topic_edge_up ON grammar_topic_edge (up_topic_id);

-- AI 提议 / 阅读卡点沉淀的知识点暂存区：采纳前不占用地图节点位。
CREATE TABLE grammar_topic_proposal (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    enable_status INTEGER NOT NULL DEFAULT 0,
    slug VARCHAR(80) NOT NULL,
    title VARCHAR(120) NOT NULL,
    world VARCHAR(80),
    world_order INTEGER,
    cefr_level VARCHAR(10),
    summary TEXT,
    prerequisites TEXT,
    content_md TEXT,
    examples_json TEXT,
    source VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    origin_article_id BIGINT,
    origin_sentence TEXT,
    raw_json TEXT
);
CREATE INDEX idx_topic_proposal_status ON grammar_topic_proposal (status, deleted);

-- 节点来源与溯源：SEED=内置大纲, USER=手动新增, AI_PROPOSED=AI 扩展, FROM_READING=阅读卡点沉淀。
ALTER TABLE grammar_topic ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'SEED';
ALTER TABLE grammar_topic ADD COLUMN origin_article_id BIGINT;
ALTER TABLE grammar_topic ADD COLUMN origin_sentence TEXT;
