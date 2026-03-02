# AgentGameFakeAppApi 摘要

> 来源：[zxx-sqq/AgentGameFakeAppApi](https://github.com/zxx-sqq/AgentGameFakeAppApi) — 租房仿真服务 API，用于 Agent 比赛/评测。

---

## 一、用途与数据范围

- **场景**：北京租房仿真。Agent 通过调用这些接口完成「查地标、查房源、租房/退租/下架」等任务。
- **数据**：北京行政区、价格约 500–25000 元/月，支持价格/户型/区域/地铁距离/西二旗通勤/商超公园等维度。
- **地标**：地铁站、世界 500 强企业、商圈（含商超/公园）。

---

## 二、硬性要求（Agent 必须遵守）

| 要求 | 说明 |
|------|------|
| **请求头** | 所有 `/api/houses/*` 接口必须带 `X-User-ID`（用户工号），否则 400。地标接口 `/api/landmarks/*` 不需要。 |
| **工号** | `X-User-ID` 必须用比赛平台注册的真实用户工号，否则用例隔离会乱、影响成绩。 |
| **数据重置** | 建议每个新 session 先调 `POST /api/houses/init`（带 `X-User-ID`），保证每次用例都是初始化数据。 |
| **租房/退租/下架** | 必须真实调用对应 API 才算完成，仅对话里写「已租」无效。 |

### 近距离约定

- **近地铁**：`subway_distance`（米）；筛选用 `max_subway_dist`，**≤800 米** 视为近地铁，**≤1000 米** 地铁可达。
- **地标附近房源**：按直线距离（米），参数 `max_distance` 默认 2000。
- **小区周边地标**：按直线距离，参数 `max_distance_m` 默认 3000。

---

## 三、接口一览（共 15 个）

**基础信息**：端口 8080；黄区/绿区 IP 见比赛说明，替换请求里的 `IP`。

### 地标（无需 X-User-ID）

| 序号 | 方法 | 路径 | 用途 |
|------|------|------|------|
| 1 | GET | /api/landmarks | 地标列表，支持 category、district |
| 2 | GET | /api/landmarks/name/{name} | 按名称精确查地标（如西二旗站、百度） |
| 3 | GET | /api/landmarks/search | 关键词模糊搜索，q 必填 |
| 4 | GET | /api/landmarks/{id} | 按 id 查地标详情 |
| 5 | GET | /api/landmarks/stats | 地标统计 |

### 房源（必须 X-User-ID）

| 序号 | 方法 | 路径 | 用途 |
|------|------|------|------|
| 6 | GET | /api/houses/{house_id} | 单套房源详情 |
| 7 | GET | /api/houses/listings/{house_id} | 该房源在各平台挂牌记录 |
| 8 | GET | /api/houses/by_community | 按小区名查可租房源 |
| 9 | GET | /api/houses/by_platform | 按平台/条件查可租房源（参数最多） |
| 10 | GET | /api/houses/nearby | 地标附近房源（需 landmark_id） |
| 11 | GET | /api/houses/nearby_landmarks | 某小区周边商超/公园 |
| 12 | GET | /api/houses/stats | 房源统计 |
| 13 | POST | /api/houses/{house_id}/rent | 租房（需 listing_platform：链家/安居客/58同城） |
| 14 | POST | /api/houses/{house_id}/terminate | 退租 |
| 15 | POST | /api/houses/{house_id}/offline | 下架 |

**数据重置**：`POST /api/houses/init`（带 X-User-ID）

---

## 四、工具定义文件

仓库内提供 **OpenAPI 3.0** 描述：`fake_app_agent_tools.json`。  
可用于：

- 生成 Agent 的 tool schema（function calling）
- 自动生成 HTTP 请求（路径、query、path 参数）

注意：实际请求需把 `servers.url` 里的 `IP` 换成比赛给定的黄区/绿区 IP。

---

## 五、与 Agent 规划的对应关系

| 规划阶段 | 在本项目中的对应 |
|----------|------------------|
| 模型接口 | 你的大模型 API（需支持 function calling 或能解析工具调用） |
| 工具接口 | 上述 15 个 HTTP 接口 + init；工具层需统一加 `X-User-ID`，并区分 houses/landmarks |
| 主循环 | 每新 session 先调 init；对话中若用户要租房/退租/下架，必须调用对应 API 并拿到成功响应再回复 |
| 配置 | 至少配置：base_url（IP:8080）、user_id（工号） |

---

## 六、FAQ（来自 README）

**Q：重复跑同一用例，第二次可租房源查不到了？**  
A：第一次执行可能把房源状态改成了已租。每次新 session 先调 `POST /api/houses/init` 做房源数据重置。
