package com.agent.housing.server;

import com.agent.housing.config.AgentConfig;
import com.agent.housing.logging.SessionLogger;
import com.agent.housing.tools.ToolExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 处理 POST /api/v1/chat 请求：解析 model_ip、session_id、message，
 * 调用模型接口；若模型返回 tool_calls，则在 8191 端执行工具，并从工具响应中解析 house_id，
 * 最终统一返回格式：session_id、response（message + houses）、status、tool_results、timestamp、duration_ms。
 */
public class ChatHandler implements com.sun.net.httpserver.HttpHandler {

    private static final Gson GSON = new Gson();
    private static final String UTF8 = "UTF-8";
    private static final AgentConfig AGENT_CONFIG = new AgentConfig();
    private static final ToolExecutor TOOL_EXECUTOR = new ToolExecutor(AGENT_CONFIG);
    private static final String MESSAGE_HOUSES_FOUND = "为您找到以下符合条件的房源：";
    private static final String MESSAGE_NO_HOUSES = "未找到符合条件的房源。";
    /** 响应中房源 ID 列表的最大数量 */
    private static final int MAX_HOUSES_IN_RESPONSE = 5;

    @Override
    public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        long startMs = System.currentTimeMillis();
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorBody("仅支持 POST"));
            return;
        }

        if (!"/api/v1/chat".equals(exchange.getRequestURI().getPath())) {
            sendJson(exchange, 404, errorBody("路径不存在"));
            return;
        }

        ChatRequest request;
        try {
            InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), UTF8);
            request = GSON.fromJson(reader, ChatRequest.class);
            reader.close();
        } catch (Exception e) {
            sendJson(exchange, 400, errorBody("请求体 JSON 解析失败: " + e.getMessage()));
            return;
        }

        if (request == null) {
            request = new ChatRequest();
        }

        String modelIp = request.getModel_ip();
        String sessionId = request.getSession_id() != null ? request.getSession_id() : "";
        String message = request.getMessage();

        if (modelIp == null || modelIp.trim().isEmpty()) {
            sendJson(exchange, 400, errorBody("model_ip 不能为空"));
            return;
        }

        SessionLogger.getInstance().logRequest(sessionId, modelIp, message);

        SessionContextManager ctx = SessionContextManager.getInstance();
        JsonArray messages = ctx.getMessagesAndAppendUser(sessionId, message);

        String modelResponse;
        try {
            modelResponse = ModelApiClient.chatWithMessages(modelIp, sessionId, messages, AGENT_CONFIG.getSystemPrompt());
        } catch (Exception e) {
            sendJson(exchange, 502, errorBody("调用模型失败: " + e.getMessage()));
            return;
        }

        JsonArray toolResults = new JsonArray();
        /** 无工具时：response 为回复字符串；有工具时：response 为转义 JSON 字符串 "{\"message\":\"...\",\"houses\":[\"HF_2101\"]}" */
        String responseValue;
        try {
            JsonObject root = GSON.fromJson(modelResponse, JsonObject.class);
            JsonArray choices = root != null ? root.getAsJsonArray("choices") : null;
            if (choices != null && choices.size() > 0) {
                JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                if (msg != null && msg.has("tool_calls")) {
                    JsonArray toolCalls = msg.getAsJsonArray("tool_calls");
                    if (toolCalls != null && toolCalls.size() > 0) {
                        ToolCallResult tcr = executeToolCallsAndCollectResults(sessionId, toolCalls);
                        toolResults = tcr.toolResults;
                        JsonObject responseContent = new JsonObject();
                        responseContent.addProperty("message", tcr.houseIds.isEmpty() ? MESSAGE_NO_HOUSES : MESSAGE_HOUSES_FOUND);
                        JsonArray houses = new JsonArray();
                        List<String> houseIdList = new ArrayList<String>();
                        Set<String> seen = new HashSet<String>();
                        for (String id : tcr.houseIds) {
                            if (id != null && !id.isEmpty() && seen.add(id)) {
                                houses.add(id);
                                houseIdList.add(id);
                                if (houseIdList.size() >= MAX_HOUSES_IN_RESPONSE) {
                                    break;
                                }
                            }
                        }
                        responseContent.add("houses", houses);
                        ctx.setLastHouseIds(sessionId, houseIdList);
                        responseValue = GSON.toJson(responseContent);
                        List<String> toolCallIds = new ArrayList<String>();
                        List<String> resultStrings = new ArrayList<String>();
                        for (JsonElement el : toolCalls) {
                            toolCallIds.add(el.getAsJsonObject().get("id").getAsString());
                        }
                        for (JsonElement el : tcr.toolResults) {
                            resultStrings.add(el.getAsJsonObject().get("result").getAsString());
                        }
                        ctx.appendAssistantAndToolResults(sessionId, msg, toolCallIds, resultStrings);
                    } else {
                        responseValue = msg.has("content") && !msg.get("content").isJsonNull() ? msg.get("content").getAsString() : "";
                        ctx.appendAssistantMessage(sessionId, msg);
                    }
                } else {
                    responseValue = (msg != null && msg.has("content") && !msg.get("content").isJsonNull()) ? msg.get("content").getAsString() : "";
                    if (msg != null) {
                        ctx.appendAssistantMessage(sessionId, msg);
                    }
                }
            } else {
                responseValue = "";
            }
        } catch (Exception e) {
            responseValue = "";
        }

        long durationMs = System.currentTimeMillis() - startMs;
        SessionLogger.getInstance().logAgentResponse(sessionId, responseValue);

        JsonObject body = new JsonObject();
        body.addProperty("session_id", sessionId);
        body.addProperty("response", responseValue);
        body.addProperty("status", "success");
        body.add("tool_results", toolResults);
        body.addProperty("timestamp", startMs / 1000);
        body.addProperty("duration_ms", durationMs);

        exchange.getResponseHeaders().put("Content-Type", Collections.singletonList("application/json; charset=UTF-8"));
        byte[] bytes = GSON.toJson(body).getBytes(UTF8);
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    /** 从会话上下文中取上一轮房源 ID，保证每轮响应都带房屋信息 */
    private static JsonArray housesArrayFromContext(SessionContextManager ctx, String sessionId) {
        JsonArray arr = new JsonArray();
        if (ctx == null) {
            return arr;
        }
        for (String id : ctx.getLastHouseIds(sessionId)) {
            arr.add(id);
        }
        return arr;
    }

    private static class ToolCallResult {
        JsonArray toolResults = new JsonArray();
        List<String> houseIds = new ArrayList<String>();
    }

    /** 租房、退租、下架：需将操作的房源 ID 放入 response.houses */
    private static final java.util.Set<String> HOUSE_OPERATION_TOOLS = new java.util.HashSet<String>(
            java.util.Arrays.asList("rent_house", "terminate_rental", "take_offline"));

    /**
     * 执行 tool_calls，收集 tool_results，并从每个工具响应的 data.items 中解析 house_id 列表。
     * 对 rent_house/terminate_rental/take_offline 从参数中取 house_id 加入 houses，便于返回操作的房源。
     * 按 session_id 记录每次工具调用与响应。
     */
    private static ToolCallResult executeToolCallsAndCollectResults(String sessionId, JsonArray toolCalls) throws Exception {
        ToolCallResult tcr = new ToolCallResult();
        SessionLogger sessionLogger = SessionLogger.getInstance();
        for (JsonElement el : toolCalls) {
            JsonObject tc = el.getAsJsonObject();
            JsonObject fn = tc.getAsJsonObject("function");
            String name = fn.get("name").getAsString();
            String arguments = fn.has("arguments") && !fn.get("arguments").isJsonNull()
                    ? fn.get("arguments").getAsString()
                    : "{}";
            sessionLogger.logToolCall(sessionId, name, arguments);
            String result = TOOL_EXECUTOR.execute(name, arguments);
            sessionLogger.logToolResponse(sessionId, name, result);
            JsonObject item = new JsonObject();
            item.addProperty("name", name);
            item.addProperty("result", simplifyToolResultToHouseIds(result));
            tcr.toolResults.add(item);
            collectHouseIdsFromResult(result, tcr.houseIds);
            if (HOUSE_OPERATION_TOOLS.contains(name)) {
                addHouseIdFromArguments(arguments, tcr.houseIds);
            }
        }
        return tcr;
    }

    /**
     * 从工具参数字符串中解析 house_id 并加入列表（不重复），插入到列表开头以保证在 response.houses 中返回。
     */
    private static void addHouseIdFromArguments(String argumentsJson, List<String> houseIds) {
        if (argumentsJson == null || argumentsJson.isEmpty()) {
            return;
        }
        try {
            JsonObject args = GSON.fromJson(argumentsJson, JsonObject.class);
            if (args != null && args.has("house_id") && !args.get("house_id").isJsonNull()) {
                String houseId = args.get("house_id").getAsString();
                if (houseId != null && !houseId.isEmpty() && !houseIds.contains(houseId)) {
                    houseIds.add(0, houseId);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 将工具返回的完整 JSON（含 data.items 房源详情）简化为仅包含 house_id 的格式，供 tool_results 返回。
     * 若为房源列表结构则返回 {"code":0,"message":"success","data":{"house_ids":["HF_1711",...]}}；否则返回原字符串。
     */
    private static String simplifyToolResultToHouseIds(String resultJson) {
        if (resultJson == null || resultJson.isEmpty()) {
            return resultJson;
        }
        try {
            JsonObject root = GSON.fromJson(resultJson, JsonObject.class);
            if (root == null || !root.has("data")) {
                return resultJson;
            }
            JsonObject data = root.getAsJsonObject("data");
            if (!data.has("items") || !data.get("items").isJsonArray()) {
                return resultJson;
            }
            JsonArray items = data.getAsJsonArray("items");
            JsonArray houseIds = new JsonArray();
            for (JsonElement e : items) {
                if (e.isJsonObject() && e.getAsJsonObject().has("house_id")) {
                    houseIds.add(e.getAsJsonObject().get("house_id"));
                }
            }
            JsonObject newData = new JsonObject();
            newData.add("house_ids", houseIds);
            JsonObject out = new JsonObject();
            out.addProperty("code", root.has("code") ? root.get("code").getAsInt() : 0);
            out.addProperty("message", root.has("message") ? root.get("message").getAsString() : "success");
            out.add("data", newData);
            return GSON.toJson(out);
        } catch (Exception e) {
            return resultJson;
        }
    }

    /**
     * 从接口响应 JSON 中解析 house_id 加入 list。
     * 支持两种格式：（1）data.items[] 列表，每项含 house_id；（2）data 为单对象且含 data.house_id（如 get_house_by_id、租房等）。
     */
    private static void collectHouseIdsFromResult(String resultJson, List<String> houseIds) {
        if (resultJson == null || resultJson.isEmpty()) {
            return;
        }
        try {
            JsonObject root = GSON.fromJson(resultJson, JsonObject.class);
            if (!root.has("data")) {
                return;
            }
            JsonElement dataEl = root.get("data");
            if (dataEl == null || !dataEl.isJsonObject()) {
                return;
            }
            JsonObject data = dataEl.getAsJsonObject();
            if (data.has("items") && data.get("items").isJsonArray()) {
                JsonArray items = data.getAsJsonArray("items");
                for (JsonElement e : items) {
                    if (e.isJsonObject() && e.getAsJsonObject().has("house_id")) {
                        houseIds.add(e.getAsJsonObject().get("house_id").getAsString());
                    }
                }
            } else if (data.has("house_id") && !data.get("house_id").isJsonNull()) {
                houseIds.add(data.get("house_id").getAsString());
            }
        } catch (Exception ignored) {
        }
    }

    private static JsonObject errorBody(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o;
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange exchange, int statusCode, JsonObject body) throws IOException {
        String utf8 = "UTF-8";
        exchange.getResponseHeaders().put("Content-Type", Collections.singletonList("application/json; charset=" + utf8));
        byte[] bytes = GSON.toJson(body).getBytes(utf8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
