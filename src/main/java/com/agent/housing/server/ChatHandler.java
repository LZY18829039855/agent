package com.agent.housing.server;

import com.agent.housing.config.AgentConfig;
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
import java.util.List;

/**
 * 处理 POST /api/v1/chat 请求：解析 model_ip、session_id、message，
 * 调用模型接口；若模型返回 tool_calls，则在 8191 端执行工具，并从工具响应中解析 house_id，
 * 最终统一返回格式：session_id、response（message + houses）、status、tool_results、timestamp、duration_ms。
 */
public class ChatHandler implements com.sun.net.httpserver.HttpHandler {

    private static final Gson GSON = new Gson();
    private static final String UTF8 = "UTF-8";
    private static final ToolExecutor TOOL_EXECUTOR = new ToolExecutor(new AgentConfig());
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

        SessionContextManager ctx = SessionContextManager.getInstance();
        JsonArray messages = ctx.getMessagesAndAppendUser(sessionId, message);

        String modelResponse;
        try {
            modelResponse = ModelApiClient.chatWithMessages(modelIp, sessionId, messages);
        } catch (Exception e) {
            sendJson(exchange, 502, errorBody("调用模型失败: " + e.getMessage()));
            return;
        }

        JsonArray toolResults = new JsonArray();
        JsonObject responseContent = new JsonObject();
        try {
            JsonObject root = GSON.fromJson(modelResponse, JsonObject.class);
            JsonArray choices = root != null ? root.getAsJsonArray("choices") : null;
            if (choices != null && choices.size() > 0) {
                JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                if (msg != null && msg.has("tool_calls")) {
                    JsonArray toolCalls = msg.getAsJsonArray("tool_calls");
                    if (toolCalls != null && toolCalls.size() > 0) {
                        ToolCallResult tcr = executeToolCallsAndCollectResults(toolCalls);
                        toolResults = tcr.toolResults;
                        responseContent.addProperty("message", tcr.houseIds.isEmpty() ? MESSAGE_NO_HOUSES : MESSAGE_HOUSES_FOUND);
                        JsonArray houses = new JsonArray();
                        int limit = Math.min(MAX_HOUSES_IN_RESPONSE, tcr.houseIds.size());
                        for (int i = 0; i < limit; i++) {
                            houses.add(tcr.houseIds.get(i));
                        }
                        responseContent.add("houses", houses);
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
                        responseContent.addProperty("message", msg.has("content") && !msg.get("content").isJsonNull() ? msg.get("content").getAsString() : "");
                        responseContent.add("houses", new JsonArray());
                        ctx.appendAssistantMessage(sessionId, msg);
                    }
                } else {
                    String content = (msg != null && msg.has("content") && !msg.get("content").isJsonNull()) ? msg.get("content").getAsString() : "";
                    responseContent.addProperty("message", content);
                    responseContent.add("houses", new JsonArray());
                    if (msg != null) {
                        ctx.appendAssistantMessage(sessionId, msg);
                    }
                }
            } else {
                responseContent.addProperty("message", "");
                responseContent.add("houses", new JsonArray());
            }
        } catch (Exception e) {
            responseContent.addProperty("message", "");
            responseContent.add("houses", new JsonArray());
        }

        long durationMs = System.currentTimeMillis() - startMs;
        JsonObject body = new JsonObject();
        body.addProperty("session_id", sessionId);
        body.add("response", responseContent);
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

    private static class ToolCallResult {
        JsonArray toolResults = new JsonArray();
        List<String> houseIds = new ArrayList<String>();
    }

    /**
     * 执行 tool_calls，收集 tool_results，并从每个工具响应的 data.items 中解析 house_id 列表。
     */
    private static ToolCallResult executeToolCallsAndCollectResults(JsonArray toolCalls) throws Exception {
        ToolCallResult tcr = new ToolCallResult();
        for (JsonElement el : toolCalls) {
            JsonObject tc = el.getAsJsonObject();
            JsonObject fn = tc.getAsJsonObject("function");
            String name = fn.get("name").getAsString();
            String arguments = fn.has("arguments") && !fn.get("arguments").isJsonNull()
                    ? fn.get("arguments").getAsString()
                    : "{}";
            String result = TOOL_EXECUTOR.execute(name, arguments);
            JsonObject item = new JsonObject();
            item.addProperty("name", name);
            item.addProperty("result", result);
            tcr.toolResults.add(item);
            collectHouseIdsFromResult(result, tcr.houseIds);
        }
        return tcr;
    }

    /**
     * 从接口响应 JSON（code、message、data.items[].house_id）中解析出所有 house_id，加入 list。
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
            JsonObject data = root.getAsJsonObject("data");
            if (!data.has("items")) {
                return;
            }
            JsonArray items = data.getAsJsonArray("items");
            for (JsonElement e : items) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("house_id")) {
                    houseIds.add(o.get("house_id").getAsString());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static JsonObject errorBody(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o;
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange exchange, int statusCode, JsonObject body) throws Exception {
        String utf8 = "UTF-8";
        exchange.getResponseHeaders().put("Content-Type", Collections.singletonList("application/json; charset=" + utf8));
        byte[] bytes = GSON.toJson(body).getBytes(utf8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
