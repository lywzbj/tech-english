-- 统一题库：题目作为独立实体进 question 表，来源用专用列（topic_id 知识点 / article_id 文章 / quiz_set_id 自测套卷）；
-- 作答流水统一进 question_attempt（append-only，错误次数按 question_key 聚合）。
-- 老表 grammar_exercise / reading_question / reading_quiz_question / exercise_attempt 保留为存档，代码不再映射。
-- answer / explanation / option_notes_json 只存服务端，判分前绝不下发（沿用原约定）。
CREATE TABLE question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted INT NOT NULL DEFAULT 0,
    enable_status INT NOT NULL DEFAULT 0,
    question_key VARCHAR(80) NOT NULL,
    domain VARCHAR(10) NOT NULL,
    topic_id BIGINT NULL,
    article_id BIGINT NULL,
    quiz_set_id BIGINT NULL,
    question_type VARCHAR(20) NOT NULL,
    pool VARCHAR(10) NULL,
    skill VARCHAR(30),
    difficulty_level INT,
    prompt TEXT NOT NULL,
    options_json TEXT NOT NULL,
    answer TEXT NOT NULL,
    explanation TEXT,
    option_notes_json TEXT,
    source VARCHAR(20) NOT NULL,
    paragraph_ref VARCHAR(40),
    sort_order INT,
    prompt_version VARCHAR(40),
    provider VARCHAR(30),
    model VARCHAR(80),
    PRIMARY KEY (id),
    UNIQUE KEY uk_question_key (question_key),
    KEY idx_question_domain_topic (domain, topic_id, pool),
    KEY idx_question_domain_article (domain, article_id, sort_order),
    KEY idx_question_quiz_set (quiz_set_id),
    CONSTRAINT fk_question_topic FOREIGN KEY (topic_id) REFERENCES grammar_topic (id),
    CONSTRAINT fk_question_article FOREIGN KEY (article_id) REFERENCES reading_article (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE question_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted INT NOT NULL DEFAULT 0,
    enable_status INT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    domain VARCHAR(10) NOT NULL,
    topic_id BIGINT NULL,
    article_id BIGINT NULL,
    question_key VARCHAR(80) NOT NULL,
    answer TEXT NOT NULL,
    correct BIT(1) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_question_attempt_user (user_id, created_at),
    KEY idx_question_attempt_user_topic (user_id, topic_id),
    KEY idx_question_attempt_key (question_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 存量搬迁：AI 语法题库原 key 即全局唯一，照搬。
INSERT INTO question (created_at, updated_at, deleted, enable_status, question_key, domain,
    topic_id, article_id, quiz_set_id, question_type, pool, skill, difficulty_level,
    prompt, options_json, answer, explanation, option_notes_json, source,
    paragraph_ref, sort_order, prompt_version, provider, model)
SELECT created_at, updated_at, deleted, enable_status, exercise_key, 'GRAMMAR',
    topic_id, NULL, NULL, exercise_type, pool, skill, difficulty_level,
    prompt, options_json, answer, explanation, option_notes_json, source,
    NULL, NULL, prompt_version, provider, model
FROM grammar_exercise;

-- 阅读静态题：answer 存选项全文，key 用 rq-<旧id>。
INSERT INTO question (created_at, updated_at, deleted, enable_status, question_key, domain,
    topic_id, article_id, quiz_set_id, question_type, pool, skill, difficulty_level,
    prompt, options_json, answer, explanation, option_notes_json, source,
    paragraph_ref, sort_order, prompt_version, provider, model)
SELECT created_at, updated_at, deleted, enable_status, CONCAT('rq-', id), 'READING',
    NULL, article_id, NULL, type, NULL, NULL, NULL,
    prompt, COALESCE(options_json, '[]'), answer, explanation, NULL, 'SEED',
    paragraph_ref, sort_order, NULL, NULL, NULL
FROM reading_question;

-- 阅读自测题：题目本体进题库（来源列 quiz_set_id + 套卷的 provider 溯源），判分结果改落 question_attempt。
INSERT INTO question (created_at, updated_at, deleted, enable_status, question_key, domain,
    topic_id, article_id, quiz_set_id, question_type, pool, skill, difficulty_level,
    prompt, options_json, answer, explanation, option_notes_json, source,
    paragraph_ref, sort_order, prompt_version, provider, model)
SELECT q.created_at, q.updated_at, q.deleted, q.enable_status, CONCAT('quiz-', q.id), 'READING',
    NULL, s.article_id, q.set_id, 'MCQ', NULL, q.skill, NULL,
    q.prompt, COALESCE(q.options_json, '[]'), q.answer, q.explanation, NULL, 'AI',
    NULL, q.sort_order, s.prompt_version, s.provider, s.model
FROM reading_quiz_question q
JOIN reading_quiz_set s ON s.id = q.set_id;

-- 作答流水：语法历史 attempt 原样平移（旧 key 由启动期 backfill 器统一改写为 seed-<topic>-<key>）。
INSERT INTO question_attempt (created_at, updated_at, deleted, enable_status, user_id, domain,
    topic_id, article_id, question_key, answer, correct)
SELECT created_at, updated_at, deleted, enable_status, user_id, 'GRAMMAR',
    topic_id, NULL, exercise_key, answer, correct
FROM exercise_attempt;

INSERT INTO question_attempt (created_at, updated_at, deleted, enable_status, user_id, domain,
    topic_id, article_id, question_key, answer, correct)
SELECT q.updated_at, q.updated_at, 0, 0, s.user_id, 'READING',
    NULL, s.article_id, CONCAT('quiz-', q.id), q.user_answer, q.is_correct
FROM reading_quiz_question q
JOIN reading_quiz_set s ON s.id = q.set_id
WHERE q.is_correct IS NOT NULL AND q.user_answer IS NOT NULL;
