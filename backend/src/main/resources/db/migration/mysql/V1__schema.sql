CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    email VARCHAR(190) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled BIT(1) NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learner_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    target_band DECIMAL(3,1) NULL,
    exam_date DATE NULL,
    daily_minutes INT NOT NULL DEFAULT 75,
    cefr_level VARCHAR(10) NOT NULL DEFAULT 'A2',
    tech_stack TEXT NULL,
    interests TEXT NULL,
    explanation_language VARCHAR(20) NOT NULL DEFAULT 'zh-CN',
    onboarding_completed BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_learner_profile_user (user_id),
    CONSTRAINT fk_learner_profile_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked BIT(1) NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_token_user (user_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE grammar_topic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    title VARCHAR(120) NOT NULL,
    world VARCHAR(80) NOT NULL,
    world_order INT NOT NULL,
    cefr_level VARCHAR(10) NOT NULL,
    summary TEXT NOT NULL,
    prerequisites TEXT NULL,
    sort_order INT NOT NULL,
    published BIT(1) NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    UNIQUE KEY uk_grammar_topic_slug (slug),
    KEY idx_grammar_topic_order (world_order, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE grammar_lesson (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    topic_id BIGINT NOT NULL,
    content_md TEXT NOT NULL,
    examples_json TEXT NULL,
    exercises_json TEXT NOT NULL,
    model VARCHAR(80) NULL,
    prompt_version VARCHAR(40) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_grammar_lesson_topic (topic_id),
    CONSTRAINT fk_grammar_lesson_topic FOREIGN KEY (topic_id) REFERENCES grammar_topic (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE grammar_progress (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    mastery_score DECIMAL(5,2) NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    correct_count INT NOT NULL DEFAULT 0,
    boss_score DECIMAL(5,2) NULL,
    last_practiced_at DATETIME(6) NULL,
    next_review_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_grammar_progress_user_topic (user_id, topic_id),
    KEY idx_grammar_progress_user (user_id),
    CONSTRAINT fk_grammar_progress_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_grammar_progress_topic FOREIGN KEY (topic_id) REFERENCES grammar_topic (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE exercise_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    exercise_key VARCHAR(80) NOT NULL,
    answer TEXT NOT NULL,
    correct BIT(1) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_exercise_attempt_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE vocab_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    lemma VARCHAR(120) NOT NULL,
    pos VARCHAR(20) NULL,
    phonetic VARCHAR(80) NULL,
    definition_en TEXT NOT NULL,
    definition_zh TEXT NOT NULL,
    example_en TEXT NULL,
    example_zh TEXT NULL,
    category VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    domain VARCHAR(40) NULL,
    source VARCHAR(120) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vocab_entry_lemma (lemma)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_vocab (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    vocab_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    ease_factor DECIMAL(4,2) NOT NULL DEFAULT 2.50,
    interval_days INT NOT NULL DEFAULT 0,
    repetitions INT NOT NULL DEFAULT 0,
    lapses INT NOT NULL DEFAULT 0,
    next_review_at DATETIME(6) NOT NULL,
    last_reviewed_at DATETIME(6) NULL,
    context_sentence TEXT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_vocab_user_word (user_id, vocab_id),
    KEY idx_user_vocab_due (user_id, next_review_at),
    CONSTRAINT fk_user_vocab_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_user_vocab_word FOREIGN KEY (vocab_id) REFERENCES vocab_entry (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE vocab_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    user_vocab_id BIGINT NOT NULL,
    rating INT NOT NULL,
    interval_before INT NOT NULL,
    interval_after INT NOT NULL,
    reviewed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_vocab_review_user (user_id, reviewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reading_article (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    title VARCHAR(200) NOT NULL,
    subtitle VARCHAR(240) NULL,
    source_type VARCHAR(20) NOT NULL,
    source_url VARCHAR(500) NULL,
    summary TEXT NULL,
    content_md TEXT NOT NULL,
    word_count INT NOT NULL,
    cefr_level VARCHAR(10) NOT NULL,
    topics_json TEXT NULL,
    estimated_minutes INT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_reading_article_type (source_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reading_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    article_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    prompt TEXT NOT NULL,
    options_json TEXT NULL,
    answer TEXT NOT NULL,
    explanation TEXT NULL,
    paragraph_ref VARCHAR(40) NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_reading_question_article (article_id, sort_order),
    CONSTRAINT fk_reading_question_article FOREIGN KEY (article_id) REFERENCES reading_article (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reading_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    minutes INT NOT NULL,
    questions_correct INT NOT NULL,
    questions_total INT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_reading_session_user (user_id, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE study_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    task_date DATE NOT NULL,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT NULL,
    ref_type VARCHAR(30) NULL,
    ref_id BIGINT NULL,
    estimated_minutes INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    sort_order INT NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_study_task_user_date (user_id, task_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    entity_type VARCHAR(30) NULL,
    entity_id BIGINT NULL,
    payload_json TEXT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_learning_event_user_time (user_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_call_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NULL,
    purpose VARCHAR(40) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    model VARCHAR(80) NULL,
    prompt_version VARCHAR(40) NULL,
    input_tokens INT NULL,
    output_tokens INT NULL,
    latency_ms INT NULL,
    status VARCHAR(20) NOT NULL,
    error_message TEXT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_call_log_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
