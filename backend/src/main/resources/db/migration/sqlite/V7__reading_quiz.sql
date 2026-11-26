-- AI 阅读自测：每次「生成」落一套题（关联用户+文章+当时小节），判分结果回填题目行。
-- answer/explanation 只存服务端，判分前绝不下发。
CREATE TABLE reading_quiz_set (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    enable_status INTEGER NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    difficulty VARCHAR(10) NOT NULL,
    section_title VARCHAR(240),
    section_ref VARCHAR(120),
    total_count INTEGER NOT NULL,
    correct_count INTEGER,
    submitted_at TIMESTAMP,
    prompt_version VARCHAR(40),
    provider VARCHAR(30),
    model VARCHAR(80),
    CONSTRAINT fk_reading_quiz_set_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_reading_quiz_set_article FOREIGN KEY (article_id) REFERENCES reading_article (id)
);
CREATE INDEX idx_reading_quiz_set_user_article ON reading_quiz_set (user_id, article_id);

CREATE TABLE reading_quiz_question (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    enable_status INTEGER NOT NULL DEFAULT 0,
    set_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL,
    prompt TEXT NOT NULL,
    options_json TEXT,
    answer TEXT NOT NULL,
    explanation TEXT,
    skill VARCHAR(30),
    user_answer TEXT,
    is_correct INTEGER,
    CONSTRAINT fk_reading_quiz_question_set FOREIGN KEY (set_id) REFERENCES reading_quiz_set (id)
);
CREATE INDEX idx_reading_quiz_question_set ON reading_quiz_question (set_id, sort_order);
