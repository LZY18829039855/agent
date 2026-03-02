# 租房仿真 Agent Tools（Java）

本模块实现 Agent 大赛租房仿真 API 对应的 16 个 Tool：5 个地标 + 10 个房源 + 1 个数据重置。

## 依赖

- Java 17+
- Maven：`mvn compile` 即可（仅依赖 Gson）。

## 配置

```java
AgentConfig config = new AgentConfig();
config.setBaseUrl("http://比赛IP:8080");  // 将 IP 换为黄区/绿区给定 IP
config.setUserId("你的工号");             // 比赛平台注册的用户工号，用于 X-User-ID
```

## 使用方式

### 1. 获取传给 LLM 的 tools 列表

```java
HousingAgentTools tools = new HousingAgentTools(config);
List<Map<String, Object>> toolSchemas = tools.getToolsForLLM();
// 将 toolSchemas 作为大模型 API 的 tools 参数（OpenAI 兼容格式）
```

### 2. 执行工具（在 Agent 主循环中）

当模型返回 tool_calls 时，根据 name 和 arguments 调用：

```java
String toolName = "get_landmark_by_name";
String argumentsJson = "{\"name\":\"西二旗站\"}";
String result = tools.execute(toolName, argumentsJson);
// 将 result 作为 tool 结果拼回对话，再继续请求模型
```

或使用 Map 参数：

```java
Map<String, Object> args = new HashMap<>();
args.put("name", "西二旗站");
String result = tools.execute(toolName, args);
```

### 3. 新 Session 时重置房源数据

建议每个新会话先调用一次：

```java
tools.execute("houses_init", "{}");
```

## 工具名称与接口对应

| 工具名 | 说明 |
|--------|------|
| get_landmarks | 地标列表 |
| get_landmark_by_name | 按名称查地标 |
| search_landmarks | 模糊搜索地标 |
| get_landmark_by_id | 按 id 查地标 |
| get_landmark_stats | 地标统计 |
| houses_init | 房源数据重置 |
| get_house_by_id | 单套房源详情 |
| get_house_listings | 房源各平台挂牌 |
| get_houses_by_community | 按小区查可租房源 |
| get_houses_by_platform | 多条件查可租房源 |
| get_houses_nearby | 地标附近房源 |
| get_nearby_landmarks | 小区周边商超/公园 |
| get_house_stats | 房源统计 |
| rent_house | 租房 |
| terminate_rental | 退租 |
| take_offline | 下架 |

## 包结构

- `config.AgentConfig`：baseUrl、userId
- `client.HousingApiClient`：HTTP 客户端，房源请求自动带 X-User-ID
- `tools.ToolDefinitions`：LLM 用 tool schema；`parseArguments` 解析 arguments JSON
- `tools.ToolExecutor`：按工具名+参数执行
- `tools.impl.LandmarkTools` / `HouseTools`：各 API 的 Java 封装
- `HousingAgentTools`：对外入口
