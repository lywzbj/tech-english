-- Soft-delete flag (0 live / 1 deleted) and status bit (0 enabled / 1 disabled)
-- on every table whose entity extends BaseEntity. INT matches Hibernate Integer
-- under JPA ddl-auto=validate.
ALTER TABLE app_user ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE learner_profile ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE refresh_token ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE grammar_topic ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE grammar_lesson ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE grammar_progress ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE exercise_attempt ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE vocab_entry ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE user_vocab ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE vocab_review ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE reading_article ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE reading_question ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE reading_session ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE study_task ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE learning_event ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE ai_call_log ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
ALTER TABLE embedding_document ADD COLUMN deleted INT NOT NULL DEFAULT 0, ADD COLUMN enable_status INT NOT NULL DEFAULT 0;
