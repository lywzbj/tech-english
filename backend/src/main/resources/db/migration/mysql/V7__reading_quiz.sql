-- AI 阅读自测：每次「生成」落一套题（关联用户+文章+当时小节），判分结果回填题目行。
-- answer/explanation 只存服务端，判分前绝不下发。
CREATE TABLE reading_quiz_set (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted INT NOT NULL DEFAULT 0,
    enable_status INT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    difficulty VARCHAR(10) NOT NULL,
    section_title VARCHAR(240),
    section_ref VARCHAR(120),
    total_count INT NOT NULL,
    correct_count INT,
    submitted_at DATETIME(6),
    prompt_version VARCHAR(40),
    provider VARCHAR(30),
    model VARCHAR(80),
    PRIMARY KEY (id),
    KEY idx_reading_quiz_set_user_article (user_id, article_id),
    CONSTRAINT fk_reading_quiz_set_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_reading_quiz_set_article FOREIGN KEY (article_id) REFERENCES reading_article (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reading_quiz_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted INT NOT NULL DEFAULT 0,
    enable_status INT NOT NULL DEFAULT 0,
    set_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    prompt TEXT NOT NULL,
    options_json TEXT,
    answer TEXT NOT NULL,
    explanation TEXT,
    skill VARCHAR(30),
    user_answer TEXT,
    is_correct INT,
    PRIMARY KEY (id),
    KEY idx_reading_quiz_question_set (set_id, sort_order),
    CONSTRAINT fk_reading_quiz_question_set FOREIGN KEY (set_id) REFERENCES reading_quiz_set (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
