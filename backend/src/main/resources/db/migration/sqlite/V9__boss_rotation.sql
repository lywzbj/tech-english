-- Boss 轮换取题：记录最近一次 BOSS 提交的题集，重考时避开刚公布过答案的题。
ALTER TABLE grammar_progress ADD COLUMN last_boss_ids TEXT;
ALTER TABLE grammar_progress ADD COLUMN last_boss_at TIMESTAMP;
