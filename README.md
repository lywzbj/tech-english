# Tech English

面向软件工程师的 AI 英语学习工作台。目标不是“背更多单词”，而是把英语变成你读技术文档、做需求沟通和备考雅思时可调用的工作能力。

当前版本是一个可运行的第一轮垂直切片：本机单用户、Java 17 + Spring Boot 3.5 + Vue 3，默认使用 SQLite（零配置启动），也可以切换到 MySQL 8；默认 AI 为本地演示模式，可切换 DeepSeek；知识库带本地向量检索（sqlite-vec）。

## 已实现

- 首次启动创建唯一账号、登录、JWT + HttpOnly refresh cookie。
- Onboarding：目标分数、考试日期、每日时长、技术栈、当前水平。
- 语法闯关：3 个世界、15 个节点、微课、技术场景例句、3 道练习、Boss 判定、掌握度和前置解锁。
- 词汇 SRS：50 个种子词、自动建 30 词初始词汇本、SM-2 四档评分、到期队列、阅读加词。
- 阅读实验室：4 篇原创文章（1 篇雅思风格 + 3 篇技术英语）、TOC、字号调整、选中词句、查词、AI 解释、阅读理解题、阅读记录；导入支持上传 Markdown 文件（拖拽或点选 .md/.markdown/.txt，自动取首个 # 标题作为文章标题）、粘贴文本和抓取网页三种方式。导入内容会在后端归一化：清理文档工具导出的 `<a id>` 历史锚点和 HTML 注释，并把文内 `#锚点` 链接改写为渲染器实际生成的标题 id，保证目录和文内跳转可用。
- 学习计划：按到期词汇、下一语法节点、未读文章生成今日任务；看板展示连续天数、本周投入、到期词、掌握度和今日任务。
- 知识搜索：文章分段和词汇自动向量化，支持 sqlite-vec KNN 检索；扩展缺失时自动退回 Java 余弦。
- AI 网关：基于 Spring AI 的 ChatClient，默认 `mock` 演示模型，可配置 DeepSeek；JSON 结构化输出、token 用量日志、失败降级。
- 双数据库：SQLite 默认（无需安装），MySQL 8 通过 `mysql` profile 启用；MySQL 用 Flyway，SQLite 用内置迁移器。
- 安全与工程：JPA、统一错误处理、CORS、SSRF 防护、Markdown 清洗、SRS 单元测试、Spring 上下文和搜索集成测试。

## 尚未实现（下一轮）

- 诊断测评（目前 onboarding 中的“当前水平”是自评）。
- 写作实验室、逐句批改、错误账本。
- 完整多轮 AI 导师、客户需求模拟。
- 雅思模考、听力、口语。
- 管理后台、开放注册、多用户部署。

这些缺口是刻意留出的。先把每天能用的语法、词汇、阅读闭环跑顺，再扩展写作和听说。

## 技术栈

- 后端：Java 17、Spring Boot 3.5.15、Spring MVC、Spring Security、Spring Data JPA、Spring AI 1.1.8、Flyway（MySQL）、Validation、Actuator、springdoc-openapi、WebClient。
- 前端：Vue 3.5、TypeScript 5.9、Vite 7、Element Plus、Pinia、Vue Router、Axios、ECharts、markdown-it、highlight.js、DOMPurify。
- 皮肤：日间（白）/ 护眼（淡黄）/ 夜间（黑底白字）三套主题，右上角一键切换，偏好存 localStorage；所有颜色走 CSS token（`styles/main.css` 的 `data-theme` 块），夜间模式同时激活 Element Plus 自带暗色变量表。
- 数据库：SQLite（默认，Hibernate community dialect）或 MySQL 8.x；MySQL 使用 `utf8mb4_0900_ai_ci`。
- 向量：sqlite-vec 0.1.9（可选扩展）+ 本地哈希嵌入；可切换 OpenAI 兼容 embeddings。
- AI：Spring AI `ChatClient` + OpenAI 兼容模型（DeepSeek 默认）；结构化输出和 token 统计由 Spring AI 处理。

## 环境要求

- JDK 17（本机路径示例：`C:\develop\java17`）
- Maven 3.9+
- Node.js 22 LTS（本机 nvm 中已有 `22.22.0`）
- MySQL 8.x 仅在你要用 `mysql` profile 时需要

当前机器上 JAVA_HOME 可能指向一个不存在的 Hive JDK，运行前请在当前终端设置：

```bash
export JAVA_HOME='C:\develop\java17'
export PATH="/c/develop/java17/bin:$PATH"
```

## 1. 启动（默认 SQLite）

```bash
./scripts/dev-backend.sh
```

等价于：

```bash
cd backend
mvn spring-boot:run
```

首次启动会：

1. 在 `backend/data/tech-english.db` 创建 SQLite 文件并应用 schema（`SqliteSchemaMigrator`）。
2. 写入 15 个语法节点、50 个词汇、4 篇文章。
3. 为文章分段和词汇建立向量索引。

数据在进程重启后保留。删除 `backend/data/tech-english.db` 可以完全重置。

可选：安装 sqlite-vec 原生扩展，让搜索走 KNN 索引而不是 Java 余弦：

```bash
./scripts/fetch-sqlite-vec.sh
```

脚本从 sqlite-vec 官方 release 下载对应平台的 `vec0.dll/.so/.dylib` 到 `backend/lib/sqlite-vec/`，并校验 SHA-256。没有扩展时应用仍可正常启动和搜索。

启动前端：

```bash
cd frontend
nvm use 22.22.0
npm install
npm run dev
```

或：

```bash
./scripts/dev-frontend.sh
```

打开 http://localhost:5173 。首次进入会要求创建唯一账号，然后设置目标。

Vite 会把 `/api` 代理到 `http://localhost:8080`。如果使用 `127.0.0.1:5173` 打开，后端 CORS 也已允许。

## 2. 可选：切换到 MySQL 8

先确保 3306 上是 MySQL 8.0+（不要使用已 EOL 的 MySQL 5.7，schema 使用了 `utf8mb4_0900_ai_ci`），然后执行：

```bash
mysql -u root -p < scripts/init-mysql.sql
```

脚本默认创建：

- 数据库：`tech_english`
- 用户：`tech_english`
- 密码：`change-me`

用 MySQL 启动后端：

```bash
./scripts/dev-backend-mysql.sh
```

等价于：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

MySQL 模式由 Flyway 管理 schema（`db/migration/mysql`），JPA 使用 `ddl-auto=validate`。SQLite 模式不启用 Flyway，因为 Flyway Community 没有 SQLite 支持。

连接信息可用 `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD` 覆盖，见 `.env.example`。

## 3. 环境变量

```bash
cp .env.example .env
```

`.env` 不会提交到 git。Spring Boot 启动时会从 `backend/.env` 或项目根目录 `.env` 读取（`spring.config.import`），也可以直接用系统环境变量覆盖。

常用变量：

```properties
JWT_SECRET=please-change-this-to-a-random-secret-at-least-32-bytes
AI_PROVIDER=mock
AI_BASE_URL=https://api.deepseek.com/v1
AI_API_KEY=
AI_MODEL=deepseek-chat

VECTOR_ENABLED=true
EMBEDDING_PROVIDER=hash
EMBEDDING_DIMENSION=384
EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_API_KEY=
```

## 4. AI 配置

AI 调用统一走 Spring AI：`ChatClient` 负责请求，`OpenAiChatModel` 负责 OpenAI Chat Completions 协议，`BeanOutputConverter` 负责把模型输出解析成 `ExplainPayload`。默认 `AI_PROVIDER=mock`，使用内置的 `MockChatModel` 返回固定演示内容，不会调用任何外部服务。

切换 DeepSeek：

```properties
AI_PROVIDER=deepseek
AI_BASE_URL=https://api.deepseek.com/v1
AI_API_KEY=sk-...
AI_MODEL=deepseek-chat
```

重启后端。API Key 只存在服务端，前端 bundle 不会包含它。

其他 OpenAI 兼容服务：把 `AI_BASE_URL` 指向其 `/v1` 兼容地址即可。地址末尾的 `/v1` 会被自动去掉，再按 Spring AI 的 `/v1/chat/completions` 路径拼接；`AI_TIMEOUT_SECONDS` 控制单次请求超时，失败时最多重试 1 次。

每次调用仍写入 `ai_call_log`：provider、model、prompt 版本、耗时、状态，以及 Spring AI 返回的 input/output token 用量。

## 5. 向量检索与嵌入

默认使用本地哈希嵌入（`EMBEDDING_PROVIDER=hash`，384 维）：

- 不需要 API Key，离线可用。
- 捕捉词面重叠，不是真正的语义理解；适合“找回学过的段落和词”。
- 文章按标题/段落切成约 900 字符的块，词汇以释义和例句建立单条记录。
- SQLite 下若 sqlite-vec 可用，使用 `vec0` 虚拟表做 KNN；否则在 Java 中计算余弦相似度。
- MySQL 下向量以 JSON 存储，检索使用 Java 余弦；`vec0` 索引仅用于 SQLite。

要换成真实语义向量：

```properties
EMBEDDING_PROVIDER=openai
EMBEDDING_BASE_URL=https://api.openai.com/v1
EMBEDDING_API_KEY=sk-...
EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_DIMENSION=384
```

重启后端后，启动索引器会检测模型/维度变化并重建向量；`vec0` 表维度不一致时会自动重建。

搜索入口是前端“知识搜索”页，或 `GET /api/v1/search?q=...`。

## 测试与构建

后端：

```bash
cd backend
mvn test
mvn -DskipTests package
```

前端：

```bash
cd frontend
npm run build
npm run test:unit
```

`npm run build` 包含 `vue-tsc --noEmit` 类型检查。生产 bundle 仍偏大（Element Plus + ECharts），后续可按路由拆包。

## 目录

```text
backend/    Spring Boot 模块化单体
  data/     默认 SQLite 数据文件（不提交）
  lib/      sqlite-vec 原生扩展（不提交）
frontend/   Vue 3 + TypeScript SPA
content/    预留的内容源目录
docs/       架构和路线图
scripts/    本地启动、数据库初始化/备份、sqlite-vec 下载
```

## 关键 API

启动后可访问：

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- 健康检查: http://localhost:8080/actuator/health

主要路由：

- `/api/v1/setup/status`、`/api/v1/setup`、`/api/v1/auth/*`、`/api/v1/me`
- `/api/v1/grammar/path`、`/api/v1/grammar/topics/{slug}`
- `/api/v1/vocabulary/due`、`/api/v1/vocabulary/{id}/review`
- `/api/v1/reading/articles`、`/api/v1/reading/import`、`/api/v1/reading/import-url`
- `/api/v1/plan/today`、`/api/v1/plan/week`
- `/api/v1/dashboard`
- `/api/v1/search`、`/api/v1/search/status`
- `/api/v1/ai/status`、`/api/v1/ai/explain`

## 设计与边界

- 单用户但所有学习数据带 `user_id`，服务层强制按当前登录用户过滤。
- AI 不直接修改掌握度、SRS 排期或解锁状态；这些由确定性服务计算。
- 学习闭环不依赖 AI：AI 不可用时，词汇复习、语法刷题、阅读答题和计划仍然可用。
- 阅读文章和 URL 导入内容视为不可信数据；Markdown 前端禁用原始 HTML 并用 DOMPurify 清洗，URL 导入阻断内网地址。
- 种子内容为原创学习材料，不复制雅思真题或受版权保护的技术文档；用户可以导入自己的材料。
- 雅思 7.0 是总分目标，包含听力和口语。当前版本只覆盖阅读/语法/词汇，不能替代官方考试；如果目标是总分 7.0，最迟第 6 个月要加入听说训练。

## 下一步建议

1. 用一周真实学习数据验证：每天是否愿意打开、今日任务是否合理、AI 解释是否真的有用。
2. 下一轮优先做诊断测评和写作批改；完整 AI 导师和客户模拟放在写作之后。
3. 需要真实语义检索时，再配置 OpenAI embeddings 并观察索引质量；本地哈希嵌入只解决“找回内容”。
4. 产品可用后再考虑拆包、PWA、听说训练和雅思模考。

更多细节见 [docs/architecture.md](docs/architecture.md) 和 [docs/roadmap.md](docs/roadmap.md)。
