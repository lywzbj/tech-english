-- 统一题库：题目作为独立实体进 question 表，来源用专用列（topic_id 知识点 / article_id 文章 / quiz_set_id 自测套卷）；
-- 作答流水统一进 question_attempt（append-only，错误次数按 question_key 聚合）。
-- 老表 grammar_exercise / reading_question / reading_quiz_question / exercise_attempt 保留为存档，代码不再映射。
-- answer / explanation / option_notes_json 只存服务端，判分前绝不下发（沿用原约定）。
CREATE TABLE question (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    enable_status INTEGER NOT NULL DEFAULT 0,
    question_key VARCHAR(80) NOT NULL,
    domain VARCHAR(10) NOT NULL,
    topic_id BIGINT,
    article_id BIGINT,
    quiz_set_id BIGINT,
    question_type VARCHAR(20) NOT NULL,
    pool VARCHAR(10),
    skill VARCHAR(30),
    difficulty_level INTEGER,
    prompt TEXT NOT NULL,
    options_json TEXT NOT NULL,
    answer TEXT NOT NULL,
    explanation TEXT,
    option_notes_json TEXT,
    source VARCHAR(20) NOT NULL,
    paragraph_ref VARCHAR(40),
    sort_order INTEGER,
    prompt_version VARCHAR(40),
    provider VARCHAR(30),
    model VARCHAR(80),
    CONSTRAINT fk_question_topic FOREIGN KEY (topic_id) REFERENCES grammar_topic (id),
    CONSTRAINT fk_question_article FOREIGN KEY (article_id) REFERENCES reading_article (id)
);
CREATE UNIQUE INDEX uk_question_key ON question (question_key);
CREATE INDEX idx_question_domain_topic ON question (domain, topic_id, pool);
CREATE INDEX idx_question_domain_article ON question (domain, article_id, sort_order);
CREATE INDEX idx_question_quiz_set ON question (quiz_set_id);

CREATE TABLE question_attempt (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    enable_status INTEGER NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    domain VARCHAR(10) NOT NULL,
    topic_id BIGINT,
    article_id BIGINT,
    question_key VARCHAR(80) NOT NULL,
    answer TEXT NOT NULL,
    correct INTEGER NOT NULL
);
CREATE INDEX idx_question_attempt_user ON question_attempt (user_id, created_at);
CREATE INDEX idx_question_attempt_user_topic ON question_attempt (user_id, topic_id);
CREATE INDEX idx_question_attempt_key ON question_attempt (question_key);

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
SELECT created_at, updated_at, deleted, enable_status, 'rq-' || id, 'READING',
    NULL, article_id, NULL, type, NULL, NULL, NULL,
    prompt, COALESCE(options_json, '[]'), answer, explanation, NULL, 'SEED',
    paragraph_ref, sort_order, NULL, NULL, NULL
FROM reading_question;

-- 阅读自测题：题目本体进题库（来源列 quiz_set_id + 套卷的 provider 溯源），判分结果改落 question_attempt。
INSERT INTO question (created_at, updated_at, deleted, enable_status, question_key, domain,
    topic_id, article_id, quiz_set_id, question_type, pool, skill, difficulty_level,
    prompt, options_json, answer, explanation, option_notes_json, source,
    paragraph_ref, sort_order, prompt_version, provider, model)
SELECT q.created_at, q.updated_at, q.deleted, q.enable_status, 'quiz-' || q.id, 'READING',
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
    NULL, s.article_id, 'quiz-' || q.id, q.user_answer, q.is_correct
FROM reading_quiz_question q
JOIN reading_quiz_set s ON s.id = q.set_id
WHERE q.is_correct IS NOT NULL AND q.user_answer IS NOT NULL;
