-- SQLite schema (default local runtime).
-- Applied by com.techenglish.common.db.SqliteSchemaMigrator, which records
-- versions in schema_version. Keep this file in sync with db/migration/mysql.

CREATE TABLE app_user (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    email VARCHAR(190) NOT NULL COLLATE NOCASE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled INTEGER NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX uk_app_user_email ON app_user (email);

CREATE TABLE learner_profile (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    target_band NUMERIC,
    exam_date DATE,
    daily_minutes INTEGER NOT NULL DEFAULT 75,
    cefr_level VARCHAR(10) NOT NULL DEFAULT 'A2',
    tech_stack TEXT,
    interests TEXT,
    explanation_language VARCHAR(20) NOT NULL DEFAULT 'zh-CN',
    onboarding_completed INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_learner_profile_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
CREATE UNIQUE INDEX uk_learner_profile_user ON learner_profile (user_id);

CREATE TABLE refresh_token (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
CREATE UNIQUE INDEX uk_refresh_token_hash ON refresh_token (token_hash);
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);

CREATE TABLE grammar_topic (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    slug VARCHAR(80) NOT NULL,
    title VARCHAR(120) NOT NULL,
    world VARCHAR(80) NOT NULL,
    world_order INTEGER NOT NULL,
    cefr_level VARCHAR(10) NOT NULL,
    summary TEXT NOT NULL,
    prerequisites TEXT,
    sort_order INTEGER NOT NULL,
    published INTEGER NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX uk_grammar_topic_slug ON grammar_topic (slug);
CREATE INDEX idx_grammar_topic_order ON grammar_topic (world_order, sort_order);

CREATE TABLE grammar_lesson (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    topic_id BIGINT NOT NULL,
    content_md TEXT NOT NULL,
    examples_json TEXT,
    exercises_json TEXT NOT NULL,
    model VARCHAR(80),
    prompt_version VARCHAR(40),
    CONSTRAINT fk_grammar_lesson_topic FOREIGN KEY (topic_id) REFERENCES grammar_topic (id)
);
CREATE UNIQUE INDEX uk_grammar_lesson_topic ON grammar_lesson (topic_id);

CREATE TABLE grammar_progress (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    mastery_score NUMERIC NOT NULL DEFAULT 0,
    attempts INTEGER NOT NULL DEFAULT 0,
    correct_count INTEGER NOT NULL DEFAULT 0,
    boss_score NUMERIC,
    last_practiced_at TIMESTAMP,
    next_review_at TIMESTAMP,
    CONSTRAINT fk_grammar_progress_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_grammar_progress_topic FOREIGN KEY (topic_id) REFERENCES grammar_topic (id)
);
CREATE UNIQUE INDEX uk_grammar_progress_user_topic ON grammar_progress (user_id, topic_id);
CREATE INDEX idx_grammar_progress_user ON grammar_progress (user_id);

CREATE TABLE exercise_attempt (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    exercise_key VARCHAR(80) NOT NULL,
    answer TEXT NOT NULL,
    correct INTEGER NOT NULL
);
CREATE INDEX idx_exercise_attempt_user ON exercise_attempt (user_id, created_at);

CREATE TABLE vocab_entry (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    lemma VARCHAR(120) NOT NULL COLLATE NOCASE,
    pos VARCHAR(20),
    phonetic VARCHAR(80),
    definition_en TEXT NOT NULL,
    definition_zh TEXT NOT NULL,
    example_en TEXT,
    example_zh TEXT,
    category VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    domain VARCHAR(40),
    source VARCHAR(120)
);
CREATE UNIQUE INDEX uk_vocab_entry_lemma ON vocab_entry (lemma);

CREATE TABLE user_vocab (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    vocab_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    ease_factor NUMERIC NOT NULL DEFAULT 2.50,
    interval_days INTEGER NOT NULL DEFAULT 0,
    repetitions INTEGER NOT NULL DEFAULT 0,
    lapses INTEGER NOT NULL DEFAULT 0,
    next_review_at TIMESTAMP NOT NULL,
    last_reviewed_at TIMESTAMP,
    context_sentence TEXT,
    CONSTRAINT fk_user_vocab_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_user_vocab_word FOREIGN KEY (vocab_id) REFERENCES vocab_entry (id)
);
CREATE UNIQUE INDEX uk_user_vocab_user_word ON user_vocab (user_id, vocab_id);
CREATE INDEX idx_user_vocab_due ON user_vocab (user_id, next_review_at);

CREATE TABLE vocab_review (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    user_vocab_id BIGINT NOT NULL,
    rating INTEGER NOT NULL,
    interval_before INTEGER NOT NULL,
    interval_after INTEGER NOT NULL,
    reviewed_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_vocab_review_user ON vocab_review (user_id, reviewed_at);

CREATE TABLE reading_article (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    title VARCHAR(200) NOT NULL,
    subtitle VARCHAR(240),
    source_type VARCHAR(20) NOT NULL,
    source_url VARCHAR(500),
    summary TEXT,
    content_md TEXT NOT NULL,
    word_count INTEGER NOT NULL,
    cefr_level VARCHAR(10) NOT NULL,
    topics_json TEXT,
    estimated_minutes INTEGER NOT NULL
);
CREATE INDEX idx_reading_article_type ON reading_article (source_type);

CREATE TABLE reading_question (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    article_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    prompt TEXT NOT NULL,
    options_json TEXT,
    answer TEXT NOT NULL,
    explanation TEXT,
    paragraph_ref VARCHAR(40),
    sort_order INTEGER NOT NULL,
    CONSTRAINT fk_reading_question_article FOREIGN KEY (article_id) REFERENCES reading_article (id)
);
CREATE INDEX idx_reading_question_article ON reading_question (article_id, sort_order);

CREATE TABLE reading_session (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    minutes INTEGER NOT NULL,
    questions_correct INTEGER NOT NULL,
    questions_total INTEGER NOT NULL
);
CREATE INDEX idx_reading_session_user ON reading_session (user_id, started_at);

CREATE TABLE study_task (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    task_date DATE NOT NULL,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT,
    ref_type VARCHAR(30),
    ref_id BIGINT,
    estimated_minutes INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    sort_order INTEGER NOT NULL,
    completed_at TIMESTAMP
);
CREATE INDEX idx_study_task_user_date ON study_task (user_id, task_date, status);

CREATE TABLE learning_event (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    entity_type VARCHAR(30),
    entity_id BIGINT,
    payload_json TEXT,
    occurred_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_learning_event_user_time ON learning_event (user_id, occurred_at);

CREATE TABLE ai_call_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    user_id BIGINT,
    purpose VARCHAR(40) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    model VARCHAR(80),
    prompt_version VARCHAR(40),
    input_tokens INTEGER,
    output_tokens INTEGER,
    latency_ms INTEGER,
    status VARCHAR(20) NOT NULL,
    error_message TEXT
);
CREATE INDEX idx_ai_call_log_user_time ON ai_call_log (user_id, created_at);

-- Vector index source of truth. The optional sqlite-vec virtual table
-- embedding_vec mirrors these rows for fast KNN search.
CREATE TABLE embedding_document (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    title VARCHAR(240) NOT NULL,
    snippet TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    model VARCHAR(80) NOT NULL,
    dimension INTEGER NOT NULL,
    vector_json TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_embedding_document_source
    ON embedding_document (source_type, source_id, chunk_index, model);
CREATE INDEX idx_embedding_document_source ON embedding_document (source_type, source_id);
