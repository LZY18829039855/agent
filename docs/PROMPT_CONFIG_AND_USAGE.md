# Agent 系统 Prompt 配置与使用说明

## 一、Prompt 是什么、起什么作用？

在本 Agent 中，**Prompt** 指发给大模型（model_ip:8888）的**系统提示词（system message）**：

- **角色**：一条 `role: "system"`、`content: "你是一段说明文字"` 的消息。
- **作用**：在每次请求模型时都放在 **messages 数组的最前面**，用来固定地告诉模型：
  - **身份**：你是租房助手、客服等；
  - **能力**：可以调用哪些工具、在什么场景下用；
  - **行为与格式**：如何组织回复、是否必须用 function calling 等。

模型会根据这段系统说明 + 后面的 user/assistant/tool 对话历史，决定是否调用工具、调用哪些、以及最终如何用自然语言回复用户。

---

## 二、在当前程序中的使用逻辑

1. **请求入口**：用户发 `POST /api/v1/chat`，带上 `model_ip`、`session_id`、`message`。
2. **拼消息**：`SessionContextManager.getMessagesAndAppendUser(sessionId, message)` 得到当前会话的 **messages**（历史 user/assistant/tool + 本轮 user）。
3. **发模型**：`ModelApiClient.chatWithMessages(modelIp, sessionId, messages)` 把 **messages** 和 **tools**（16 个工具 schema）一起发给 `model_ip:8888/v1/chat/completions`。
4. **系统 Prompt 的插入点**：在把 messages 发给模型**之前**，若配置了系统提示词，则在 messages 数组的**最前面**插入一条：
   ```json
   { "role": "system", "content": "这里是你配置的系统提示词" }
   ```
5. **模型侧**：模型每次都会先读这条 system，再读后面的对话，从而在整轮（包括多轮 tool call）中保持「身份 + 行为」一致。

也就是说：**Prompt 的使用逻辑 = 在发给模型的 messages 最前面固定加一条 system 消息，内容来自你的配置。**

---

## 三、如何配置 Prompt？

### 方式一：通过系统属性（推荐，无需改代码）

在启动 8191 服务时加 JVM 参数。**完整版租房 Prompt** 见 [SYSTEM_PROMPT_HOUSING.md](./SYSTEM_PROMPT_HOUSING.md)，以下为缩短版示例：

```bash
java -Dhousing.agent.systemPrompt="你是北京租房助手，帮助用户在北京查房、租房。新会话建议先调 houses_init；查房先查地标再 get_houses_nearby 或 get_houses_by_platform；租房/退租/下架必须调用对应 API。回复简洁，包含价格、面积、位置、平台。近地铁用 max_subway_dist=800。" -jar your-agent.jar
```

或在代码里设置：

```java
System.setProperty("housing.agent.systemPrompt", "你是租房助手...");
```

若**不设置**该属性，则不会插入 system 消息，行为与当前一致（仅靠工具描述约束模型）。

### 方式二：在 AgentConfig 中写死或从配置文件读

- 在 `AgentConfig` 中增加字段 `systemPrompt`，从 `application.properties` / `agent.yaml` 等读入（若你后续引入配置文件）。
- 在 `ModelApiClient.chatWithMessages` 中通过 `AgentConfig` 拿到 `systemPrompt`，非空时在 messages 前插入 system 消息。

---

## 四、在程序中的具体使用位置

| 步骤 | 位置 | 说明 |
|------|------|------|
| 读取配置 | `AgentConfig.getSystemPrompt()` | 系统属性 `housing.agent.systemPrompt` 或配置文件 |
| 插入 system 消息 | `ModelApiClient.chatWithMessages()` | 在构建请求体时，若 systemPrompt 非空，则在 `messages` 最前面插入 `{ "role": "system", "content": systemPrompt }` |
| 发送请求 | 同上 | 请求体为 `{ "model": "", "messages": [system, ...历史, 本轮user], "tools": [...], "stream": false }` |

这样，**配置一次、每次请求都会自动带上**，无需在业务代码里重复写。

---

## 五、小结

| 问题 | 答案 |
|------|------|
| Prompt 是什么？ | 发给模型的**系统提示词**，即一条 `role: "system"` 的 message。 |
| 使用逻辑？ | 在每次请求模型的 **messages 最前面** 插入这条 system，模型据此约束身份与行为。 |
| 如何配置？ | 系统属性 `housing.agent.systemPrompt`，或扩展 AgentConfig/配置文件。 |
| 在程序中哪里用？ | 在 `ModelApiClient.chatWithMessages` 构建请求体时，根据配置在 messages 前追加 system 消息。 |
