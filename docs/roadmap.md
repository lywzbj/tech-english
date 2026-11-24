# Roadmap

## Done in the first vertical slice

- Local single-user setup/login.
- Onboarding self-assessment.
- Grammar path with 15 nodes and mastery/unlock.
- Vocabulary SRS with SM-2.
- Reading lab with IELTS-style and technical articles, TOC, word lookup, AI explanation and comprehension questions.
- Daily plan and dashboard.
- Mock/DeepSeek-compatible AI gateway.
- SQLite default runtime (zero setup) with opt-in MySQL 8 profile.
- Local vector search over article chunks and vocabulary, with sqlite-vec KNN and Java cosine fallback.

## Next: diagnostic and writing

1. Diagnostic session: adaptive grammar/vocabulary/reading items plus a short writing sample.
2. Store per-skill estimates and recommended starting node.
3. Writing lab: IELTS Task 1/2 and work-English tasks, rubric feedback, sentence corrections and error ledger.
4. Weekly review that reads the error ledger and adjusts the next plan.

## Then: tutor and communication

1. Multi-turn tutor with context selection (article, grammar node, writing draft).
2. SSE streaming and conversation persistence.
3. Text-based client requirement simulator: hidden requirements, clarifying questions, final summary rubric.
4. Later: voice input/output and speaking feedback.

## Before an official IELTS 7.0 attempt

- Add listening and speaking practice. Overall band 7.0 includes both.
- Add timed mock reading/writing and score trend.
- Keep the official test as the source of truth; AI band estimates are practice signals only.

## Engineering follow-ups

- Verify the MySQL 8 profile end to end (Flyway migration + Hibernate `validate`) on a real instance.
- Evaluate a real embedding model against the hashing baseline; track retrieval quality per query set.
- Generate TypeScript API types from OpenAPI instead of hand-maintained interfaces.
- Add Playwright E2E with `AI_PROVIDER=mock`.
- Add per-user AI rate limits and daily token budgets.
- Code-split Element Plus/ECharts if startup time becomes noticeable.
- Add data export/import and scheduled backups for SQLite and MySQL.
