# LLM 提示词目录

后端的**所有** AI 提示词集中存放在这里，由 `com.techenglish.ai.PromptRegistry` 在应用
启动时全量读入内存；请求路径不再读磁盘。

## 目录与命名约定

```
prompts/<purpose>/<version>-system.md   系统级提示词（角色设定 + 任务规则 + 输出 JSON 结构）
prompts/<purpose>/<version>-user.md     对话级提示词（用户消息模板，含 {{name}} 占位符）
```

- `<purpose>`：用途目录名，小写中划线（`explain`、`grammar-explain`、`question-gen` …），
  同时是配置键 `app.ai.prompts.<purpose>` 和 `ai_call_log.purpose` 的来源。
- `<version>`：`v1`、`v2` … 递增。**不原地修改已生效文件**——新版本另存一对文件，
  改配置切换，随时可回滚。旧版本文件留在目录里作为档案。
- 占位符语法只有 `{{name}}`（纯文本替换）。渲染后若仍残留 `{{...}}` 会直接抛异常，
  不会把残缺提示词发给模型。
- 系统级文件不放占位符；面向输出格式的 JSON 结构写在系统级文件末尾（JSON shape）。

## 当前登记的用途

| purpose | 说明 | 生效版本 |
|---|---|---|
| explain | 阅读文章选中词句的讲解 | v2 |
| grammar-explain | 语法知识解读（由浅入深） | v1 |
| writing-review | 英文写作批改 | v1 |
| question-gen | 按文章类型/内容生成阅读理解题 | v1 |
| exercise-gen | 按语法知识点生成由浅入深练习题 | v1 |
| ability-assess | 依据学习统计评估能力并给出建议 | v1 |
| preview | 阅读前预习单（核心词 10~15 + 结构预告 ≤3） | v1 |

## 新增一个用途

1. 建目录并写一对 `v1-system.md` / `v1-user.md`。
2. 在 `AiProperties.prompts` 默认值、`application.yml` 的 `app.ai.prompts`
   （带 `AI_PROMPT_VERSION_<PURPOSE>` 环境变量覆盖）中登记。
3. 启动校验是 fail-fast 的：文件缺失或为空白会导致应用拒绝启动。
