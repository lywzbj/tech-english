-- AI 语法题库：按 topic + pool 缓存生成题。
-- answer / explanation / option_notes_json 只存服务端，判分前绝不下发。
CREATE TABLE grammar_exercise (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    enable_status INTEGER NOT NULL DEFAULT 0,
    exercise_key VARCHAR(80) NOT NULL,
    topic_id BIGINT NOT NULL,
    pool VARCHAR(10) NOT NULL,
    exercise_type VARCHAR(20) NOT NULL,
    skill VARCHAR(30),
    difficulty_level INTEGER,
    prompt TEXT NOT NULL,
    options_json TEXT NOT NULL,
    answer TEXT NOT NULL,
    explanation TEXT,
    option_notes_json TEXT,
    source VARCHAR(20) NOT NULL,
    prompt_version VARCHAR(40),
    provider VARCHAR(30),
    model VARCHAR(80),
    CONSTRAINT fk_grammar_exercise_topic FOREIGN KEY (topic_id) REFERENCES grammar_topic (id)
);
CREATE UNIQUE INDEX uk_grammar_exercise_key ON grammar_exercise (exercise_key);
CREATE INDEX idx_grammar_exercise_topic_pool ON grammar_exercise (topic_id, pool);
