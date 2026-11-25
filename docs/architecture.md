# Architecture

Tech English is a modular monolith built for one learner first, with multi-user-ready data boundaries.

## Runtime

```text
Vue 3 SPA (Vite)
  └─ /api proxy → Spring Boot :8080
Spring Boot 3.5 / Java 17
  ├─ identity        setup, login, JWT, refresh cookie, profile
  ├─ grammar         topic tree, lessons, exercises, mastery
  ├─ vocabulary      word bank, SM-2 scheduling
  ├─ reading         articles, questions, import, reading sessions
  ├─ plan            daily tasks, completion
  ├─ dashboard       aggregate learning state
  ├─ vector          embeddings, sqlite-vec KNN, keyword fallback
  ├─ ai              Spring AI ChatClient gateway, prompt + validation + logs
  └─ seed            first-run content importer
SQLite (default) / MySQL 8 (mysql profile)
```

## Backend rules

- Package by feature, not by layer. Each module owns its controller, service, repository, entities and DTOs.
- SQLite is the default runtime. Flyway Community has no SQLite dialect, so `SqliteSchemaMigrator` applies `db/migration/sqlite` and records versions in `schema_version`. JPA runs with `ddl-auto=none` there.
- MySQL is opt-in through the `mysql` profile: Flyway applies `db/migration/mysql` and JPA runs with `ddl-auto=validate`.
- AI calls do not run inside long database transactions. The server reads context, calls AI, validates output, then persists in a transaction.
- Learning state is deterministic: answer grading, SM-2 intervals, unlock conditions and plan minutes are computed by services, not by the model.
- All learner data is scoped by `user_id`. Do not trust a userId supplied by the client.
- The app remains usable when AI is unavailable: vocabulary, grammar, reading questions and plans work without it.

## AI gateway

- `AiGateway` is the only place that calls a model. It uses Spring AI's `ChatClient`; `AiClientConfiguration` builds an `OpenAiChatModel` from `app.ai.*` for `AI_PROVIDER=deepseek|openai`.
- The OpenAI starter auto-configuration is intentionally not used: it requires an API key at startup, while this app must run in mock mode with no key.
- `AI_PROVIDER=mock` wires a `MockChatModel` (a Spring AI `ChatModel`) instead, so mock and remote share one code path.
- Chat options request JSON mode (`response_format: json_object`). `BeanOutputConverter<ExplainPayload>` parses the reply and tolerates Markdown fences; malformed JSON degrades to plain text instead of failing the request.
- `prompts/explain/v1.md` is versioned. `AiCallLog` records provider, model, prompt version, latency, status and input/output token usage from Spring AI metadata.
- Requests use the configured `AI_TIMEOUT_SECONDS` read timeout and one Spring AI retry for transient failures.
- Imported text is treated as untrusted data; it is never merged into the system prompt as instructions.
- Imported markdown is normalized at write time (`MarkdownNormalizer`): empty HTML anchors (`<a id="s2-foo"></a>`) and comments are stripped, and in-document `#anchor` links are retargeted to the heading ids markdown-it-anchor generates (levels 2-5, slug contract in frontend `utils/markdown.ts`). Clean markdown is the single stored source; the renderer keeps `html:false`.
- Markdown is sanitized in the browser with DOMPurify. URL import blocks loopback, private and link-local addresses.

## Vector index

- `EmbeddingService` uses the local hashing embedder by default (384 dimensions, no API key) and can call an OpenAI-compatible `/embeddings` endpoint when `EMBEDDING_PROVIDER=openai`.
- `VectorSearchService` indexes article chunks and vocabulary entries at startup and after article import (`ArticleImportedEvent`, after commit). Each chunk stores a SHA-256 hash so unchanged text is not re-embedded.
- On SQLite, `SqliteVecIndex` loads the optional `vec0` extension and keeps a `vec0` virtual table aligned with `embedding_document` row ids. KNN results are converted from L2 distance to cosine score.
- If the extension is missing or fails, search falls back to Java cosine over stored JSON vectors, then to token-overlap keyword search if the embedding provider is unavailable.
- MySQL stores vectors as JSON and uses the Java path; no vector-specific MySQL type is required.

## Data groups

```text
identity:  app_user, learner_profile, refresh_token
content:   grammar_topic, grammar_lesson, vocab_entry, reading_article, reading_question
learning:  grammar_progress, exercise_attempt, user_vocab, vocab_review,
           reading_session, learning_event
planning:  study_task
AI:        ai_call_log
vector:    embedding_document (+ optional SQLite vec0 table embedding_vec)
```

Flexible content such as exercises, options and examples is stored as JSON strings in `TEXT` columns. Fields used for filtering, sorting or scheduling are explicit indexed columns.

## Frontend

- Vue 3 + TypeScript strict mode + Vite.
- Element Plus provides form and dialog primitives; the reading and path views are custom.
- Pinia owns auth/profile state. Access JWTs live in memory; refresh tokens are HttpOnly cookies.
- `http.ts` performs single-flight refresh-token rotation on 401 and dispatches `auth:expired` if refresh fails.
- Article markdown is rendered with `markdown-it` + `markdown-it-anchor`, highlighted with a small set of highlight.js languages, and sanitized with DOMPurify.
- `useTaskCompletion` marks the matching daily task complete after grammar, vocabulary or reading work.
- `SearchView` queries `/api/v1/search`, links article hits to the reader and vocabulary hits to the filtered word bank.
- Skins: three themes (white day / pale-yellow eye-comfort / black night) selected via `ThemeSwitcher` in the topbar. All colors live in `styles/main.css` as CSS custom properties switched by `html[data-theme]`; `composables/useTheme.ts` persists the choice and toggles Element Plus's `dark` class (plus its own dark css-vars import) at night. Colors must be added as tokens here — components must not hardcode hex.

## Deliberate omissions

No Redis, message queue, external vector service or microservices in this version. The vector index is local SQLite and the embedding model is swappable; add a dedicated vector database only after real usage shows the need.
