-- AI 语法题库：按 topic + pool 缓存生成题。
-- answer / explanation / option_notes_json 只存服务端，判分前绝不下发。
CREATE TABLE grammar_exercise (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted INT NOT NULL DEFAULT 0,
    enable_status INT NOT NULL DEFAULT 0,
    exercise_key VARCHAR(80) NOT NULL,
    topic_id BIGINT NOT NULL,
    pool VARCHAR(10) NOT NULL,
    exercise_type VARCHAR(20) NOT NULL,
    skill VARCHAR(30),
    difficulty_level INT,
    prompt TEXT NOT NULL,
    options_json TEXT NOT NULL,
    answer TEXT NOT NULL,
    explanation TEXT,
    option_notes_json TEXT,
    source VARCHAR(20) NOT NULL,
    prompt_version VARCHAR(40),
    provider VARCHAR(30),
    model VARCHAR(80),
    PRIMARY KEY (id),
    UNIQUE KEY uk_grammar_exercise_key (exercise_key),
    KEY idx_grammar_exercise_topic_pool (topic_id, pool),
    CONSTRAINT fk_grammar_exercise_topic FOREIGN KEY (topic_id) REFERENCES grammar_topic (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
