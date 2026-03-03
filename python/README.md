# 租房 Agent（Python 服务）

与仓库内 Java 服务同仓的 Python 租房 Agent，基于 Flask + LangChain，对接同一套租房仿真 API。

## 环境

- Python 3.8+
- 使用 [uv](https://github.com/astral-sh/uv) 管理虚拟环境与依赖

## 快速开始

```bash
# 在仓库根目录或 python 目录下
uv venv
# Windows
.venv\Scripts\activate
# Linux/macOS
# source .venv/bin/activate

uv sync
# 需在 python 目录下执行
uv run flask --app housing_agent.app run
```

## 配置

- 默认端口：**8191**（与 Java 服务一致，可通过环境变量 `FLASK_RUN_PORT` 覆盖）
- 租房仿真 API：环境变量 `HOUSING_API_BASE_URL`（默认 `http://7.225.29.223:8080`）
- 工号：`HOUSING_API_USER_ID`（默认 `l00845559`）
- 系统提示词：`HOUSING_AGENT_SYSTEM_PROMPT`（可选，有默认北京租房助手 Prompt）
- 模型 API 由请求体中的 `model_ip` 指定（如 `http://127.0.0.1` 或 `127.0.0.1`，端口固定 8888）

## 接口（与 Java Agent 一致）

启动后服务地址为 **http://127.0.0.1:8191**（默认端口 8191）。

### POST /api/v1/chat

**请求体**（JSON）：

- `model_ip`（必填）：模型服务地址，如 `127.0.0.1` 或 `http://127.0.0.1`
- `session_id`（可选）：会话 ID，同 ID 共享多轮历史
- `message`（可选）：本轮用户消息

**响应**（JSON）：

- `session_id`：会话 ID
- `response`：**字符串**，根据是否有工具调用有两种形式：
  - **普通对话**（无工具调用）：自然语言文本，如 `"您好，请问有什么可以帮您？"`
  - **房源查询完成后**（有工具调用）：合法 JSON 字符串（已转义），如 `"{\"message\": \"为您找到以下符合条件的房源：\", \"houses\": [\"HF_4\", \"HF_6\"]}"`
- `status`：`success` 或 `error`
- `tool_results`：`[{ "name": "工具名", "result": "API 返回 JSON 字符串" }, ...]`
- `timestamp`：请求开始时间（秒）
- `duration_ms`：耗时（毫秒）

### GET /health

健康检查，返回 `{"status":"ok","service":"housing-agent-python"}`。

## 日志

- 每次请求、工具调用及工具输出会按 **session_id** 分块写入日志。
- 日志目录：**当前工作目录下的 `logs/`**（例如在 `python` 目录启动则为 `python/logs/`）。
- 文件名：`housing_agent_YYYY-MM-DD.log`（按天滚动）。
- 每条记录包含 `[session_id=xxx]`，便于按会话筛选。

## 设计文档

详见 [../docs/PYTHON_HOUSING_AGENT_DESIGN.md](../docs/PYTHON_HOUSING_AGENT_DESIGN.md)。
