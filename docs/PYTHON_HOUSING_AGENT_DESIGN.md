# Python 租房 Agent 设计文档

> 与仓库内 Java 租房 Agent 同仓的 Python 实现，基于 **Flask** 与 **LangChain** 系列框架，对接同一套租房仿真 API（AgentGameFakeAppApi）。本文档只做架构与设计说明，不涉及 Java 代码。

---

## 一、目标与范围

- **目标**：在同一代码仓下提供一套可独立运行的「租房 Agent」Python 服务，与现有 Java 服务并存；能力上与 Java 版对齐：模型驱动工具调用、多轮对话、完整执行租房仿真 API。
- **范围**：
  - HTTP 服务：Flask 提供 REST API（如 `/api/v1/chat`、健康检查）。
  - Agent 核心：LangChain 编排「模型 → 解析 tool_calls → 执行工具 → 再调模型」的循环，直到模型返回最终文本。
  - 工具层：16 个工具（5 地标 + 10 房源 + 1 数据重置），通过 `requests` 调用租房仿真 API，房源类请求统一带 `X-User-ID`。
  - 配置与安全：API 地址、工号、模型 endpoint 等外部化，不写死密钥。

---

## 二、技术栈与依赖

| 依赖 | 版本约束 | 用途 |
|------|----------|------|
| **flask** | >=2.3.0 | HTTP 服务、路由、请求/响应封装 |
| **requests** | >=2.28.0 | 调用租房仿真 API（地标/房源/init/租房/退租/下架） |
| **langchain** | >=0.2.0 | Agent 编排、链式调用、与 OpenAI 兼容模型对接 |
| **langchain-openai** | >=0.0.5 | 与 OpenAI 兼容的 Chat 模型封装（可配置 base_url 指向自建模型） |
| **langchain-core** | >=0.2.0 | 消息、工具、运行接口等核心抽象 |
| **langchain-community** | >=0.0.10 | 可选：额外工具/集成，便于后续扩展 |

运行环境：Python 3.10+，推荐使用 **uv** 管理虚拟环境（`uv venv` + `uv sync`）。

---

## 三、整体架构

```
                    ┌─────────────────────────────────────────────────────────┐
  HTTP 请求          │                    Flask 应用                           │
  (POST /api/v1/chat)│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐   │
  ──────────────────►│  │ 路由/中间件  │ → │ Agent 编排   │ → │ 工具执行层   │   │
                     │  │ (session_id, │   │ (LangChain) │   │ (requests   │   │
                     │  │  message)   │   │ 模型+循环    │   │  调仿真API)  │   │
                     │  └─────────────┘   └──────┬──────┘   └──────┬──────┘   │
                     │                           │                  │          │
                     │                           │  tool_calls      │ 结果     │
                     │                           └──────────────────┘          │
                     └─────────────────────────────────────────────────────────┘
                                          │
                     ◄────────────────────┘
                     响应：最终回复文本 / 或结构化 JSON（含 content）
```

- **Flask 层**：接收聊天请求，解析 `session_id`、`message`，可选维护会话状态（内存或后续扩展 Redis 等）；调用 Agent 层得到最终回复后返回。
- **LangChain Agent 层**：维护 `messages` 列表（user / assistant / tool），每轮将「当前消息 + 工具 schema」发给模型；若返回 `tool_calls`，则调用工具层执行，把结果以 `tool` 角色追加到消息中，再请求模型；直到模型不再返回 `tool_calls`，此时 `content` 即为 Agent 最终回复。
- **工具层**：根据 `(tool_name, arguments)` 调用租房仿真 API（GET/POST），房源类请求统一加上请求头 `X-User-ID`；返回统一格式的字符串或结构化摘要，供 LangChain 拼回对话。

与仓库内 [AGENT_EXECUTION_LOGIC.md](./AGENT_EXECUTION_LOGIC.md) 中「完整 Agent 循环」一致：**谁选工具**由模型决定，**谁执行工具**由本 Python 服务内的工具执行层完成。

---

## 四、推荐目录与模块划分

```
python/
├── pyproject.toml              # 依赖：flask, requests, langchain*, uv 管理
├── README.md
├── src/
│   └── housing_agent/
│       ├── __init__.py
│       ├── app.py              # Flask 应用创建、注册蓝图/路由
│       ├── config.py           # 从环境变量/文件读取 base_url、user_id、模型 endpoint 等
│       ├── agent/
│       │   ├── __init__.py
│       │   ├── loop.py         # Agent 主循环：消息列表维护、调用模型、解析 tool_calls、调用工具、再调模型
│       │   ├── prompts.py      # 系统提示词（与 docs/SYSTEM_PROMPT_HOUSING.md 对齐）
│       │   └── state.py        # 可选：会话状态（如 session_id -> messages）
│       ├── models/
│       │   ├── __init__.py
│       │   └── adapter.py      # LangChain ChatModel 封装（OpenAI 兼容 base_url + api_key）
│       └── tools/
│           ├── __init__.py
│           ├── registry.py    # 工具注册、向 LangChain 提供 tools 列表（name/description/parameters）
│           ├── executor.py    # 根据 name + arguments 调仿真 API，统一 X-User-ID、错误处理
│           └── definitions/   # 可选：按工具分文件；或统一在 registry 中定义 schema
│               └── schemas.py # 16 个工具的 JSON Schema / function 描述，与 Java ToolDefinitions 对齐
├── tests/
│   ├── test_agent_loop.py
│   ├── test_tools.py
│   └── test_app.py
└── docs/                       # 或引用仓库根 docs/
    └── (本设计文档放在仓库根 docs/PYTHON_HOUSING_AGENT_DESIGN.md)
```

- **config**：`HOUSING_API_BASE_URL`、`HOUSING_API_USER_ID`、模型相关（如 `OPENAI_API_BASE`、`OPENAI_API_KEY`、`MODEL_NAME`），与 Java 侧 `AgentConfig` 语义一致。
- **agent/loop**：实现「请求模型 → 若有 tool_calls 则执行并追加消息 → 再请求模型」的循环，并设置最大步数、超时等终止条件。
- **tools/executor**：用 `requests` 发 HTTP，地标类用 `base_url`，房源类在 headers 中加 `X-User-ID`；与 [AgentGameFakeAppApi_摘要.md](./AgentGameFakeAppApi_摘要.md) 及 Java `HousingApiClient`/`ToolExecutor` 行为一致。

---

## 五、模型适配层（LangChain）

- 使用 **langchain-openai** 的 `ChatOpenAI`（或同系列），通过 `base_url` 指向现有模型服务（如 `model_ip:8888`），使之与 Java 使用的同一模型 API 一致。
- 将 16 个工具的 **function** 描述（name、description、parameters 的 JSON Schema）传给模型；模型需支持 **function calling / tool use**，返回中带 `tool_calls`。
- 统一响应结构：从模型返回中解析出 `content`（文本）和 `tool_calls`（name、arguments），供 Agent 循环使用。若模型返回格式与 OpenAI 不完全一致，可在 adapter 中做一层薄封装，转成 LangChain 期望的 `AIMessage`（含 `tool_calls`）。

这样与 [AGENT_PROJECT_PLAN.md](./AGENT_PROJECT_PLAN.md) 中「阶段 2：模型接口适配层」对应：封装为 `chat(messages, tools)`，内部走 LangChain + 你的模型 endpoint。

---

## 六、工具层设计

- **Schema**：16 个工具的名称、描述、参数与 Java `ToolDefinitions` 保持一致，便于模型行为一致；可直接参考仓库内 `README_TOOLS.md` 与 `ToolDefinitions.java`，或仿真仓库的 `fake_app_agent_tools.json`。
- **执行**：
  - 地标类：`GET /api/landmarks/*`，不需 `X-User-ID`。
  - 房源类：`GET/POST /api/houses/*` 及 `POST /api/houses/init`，请求头必须带 `X-User-ID`。
- **新会话**：建议在首轮用户消息后、查房前先调用 `houses_init`，与 Java 侧和 [SYSTEM_PROMPT_HOUSING.md](./SYSTEM_PROMPT_HOUSING.md) 一致。
- **租房/退租/下架**：必须真实调用对应 API，成功后再由模型生成「已租」等回复；仅对话中写「已租」无效。
- **返回格式**：工具执行结果建议为字符串（或 LangChain 可接受的 tool message 内容），便于拼回 `messages` 进入下一轮模型调用。

---

## 七、Agent 主循环（LangChain 编排）

1. **输入**：当前会话的 `messages`（或仅当前轮 user 消息 + 历史摘要）、16 个工具的 schema。
2. **调用模型**：`messages` + `tools` 请求模型。
3. **判断返回**：
   - 无 `tool_calls`：将本次 `content` 作为 **Agent 最终回复**，结束并返回。
   - 有 `tool_calls`：对每个 tool call 用 **executor** 执行，得到结果后拼成 `tool` 消息追加到 `messages`。
4. 重复 2～3，直到某次模型只返回文本或达到最大步数/超时。
5. **输出**：最终回复文本；对外接口可包成 `{ "reply": "..." }` 或与 Java 接口格式统一。

错误与重试：工具调用失败时可将错误信息作为 tool 结果返回给模型，由模型决定重试或换策略；必要时在 executor 内做有限次重试与超时控制。

---

## 八、Flask API 设计建议

- `GET /health`：健康检查，便于部署与网关探测。
- `POST /api/v1/chat`：与 Java 侧语义对齐；请求体建议包含 `session_id`、`message`（或 `messages`）；响应体包含最终回复，例如 `{ "reply": "..." }` 或兼容现有前端的结构。
- 可选：`POST /api/v1/session/init`，在创建新会话时主动调一次 `houses_init`，再由前端发首条用户消息。

配置通过环境变量或配置文件注入，不写死在代码中；密钥类仅从环境变量读取。

---

## 九、与 Java 服务的关系

| 维度 | 说明 |
|------|------|
| **代码仓** | 同一仓库；Java 在 `src/main/java/...`，Python 在 `python/` 下，互不覆盖。 |
| **仿真 API** | 共用同一套租房仿真 API（base_url + 工号），行为与 [AgentGameFakeAppApi_摘要.md](./AgentGameFakeAppApi_摘要.md) 一致。 |
| **模型** | 可共用同一模型服务（如 model_ip:8888），仅通过不同进程（Java/Python）调用。 |
| **部署** | 可独立部署：Java 服务一个端口，Python 服务另一端口；或只运行其一。 |
| **文档** | 系统 Prompt、执行逻辑、工具列表等与现有 `docs/` 保持一致，本设计文档仅描述 Python 实现方式。 |

---

## 十、阶段与优先级建议

- **MVP**：Flask 应用 + 配置加载 + 模型适配（LangChain ChatOpenAI 兼容）+ 16 个工具 schema 与 executor + Agent 主循环 + 系统 Prompt；实现单会话多轮 tool call 并返回最终回复。
- **后续**：会话持久化（如按 session_id 存 messages）、日志与追踪、单元测试与端到端用例、与 Java 接口格式统一等。

当前仓库已具备模型接口说明、工具接口列表、系统 Prompt 与执行逻辑文档，Python 实现可直接按本设计在 `python/` 下迭代，无需修改 Java 代码。
