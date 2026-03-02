package com.agent.housing.server;

import com.agent.housing.config.AgentConfig;
import com.agent.housing.tools.ToolExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Collections;

/**
 * 处理 POST /api/v1/chat 请求：解析 model_ip、session_id、message，
 * 调用模型接口；若模型返回 tool_calls，则在 8191 端执行对应工具，直接将工具接口的响应返回，不再请求模型。
 */
public class ChatHandler implements com.sun.net.httpserver.HttpHandler {

    private static final Gson GSON = new Gson();
    private static final String UTF8 = "UTF-8";
    private static final ToolExecutor TOOL_EXECUTOR = new ToolExecutor(new AgentConfig());

    @Override
    public void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.lang.Exception {
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
        String sessionId = request.getSession_id();
        String message = request.getMessage();

        if (modelIp == null || modelIp.trim().isEmpty()) {
            sendJson(exchange, 400, errorBody("model_ip 不能为空"));
            return;
        }

        String modelResponse;
        try {
            modelResponse = ModelApiClient.chat(modelIp, sessionId, message);
        } catch (Exception e) {
            sendJson(exchange, 502, errorBody("调用模型失败: " + e.getMessage()));
            return;
        }

        String responseToSend = modelResponse;
        try {
            JsonObject root = GSON.fromJson(modelResponse, JsonObject.class);
            JsonArray choices = root != null ? root.getAsJsonArray("choices") : null;
            if (choices != null && choices.size() > 0) {
                JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                if (msg != null && msg.has("tool_calls")) {
                    JsonArray toolCalls = msg.getAsJsonArray("tool_calls");
                    if (toolCalls != null && toolCalls.size() > 0) {
                        responseToSend = executeToolCallsAndBuildResponse(toolCalls);
                    }
                }
            }
        } catch (Exception e) {
            responseToSend = modelResponse;
        }

        exchange.getResponseHeaders().put("Content-Type", Collections.singletonList("application/json; charset=UTF-8"));
        byte[] bytes = responseToSend.getBytes(UTF8);
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    /**
     * 执行 tool_calls 中的每个工具，并将接口响应返回。
     * 仅一个工具时直接返回该工具接口的响应字符串；多个时返回 {"tool_results": [{"name":"...","result":"..."}]}。
     */
    private static String executeToolCallsAndBuildResponse(JsonArray toolCalls) throws Exception {
        JsonArray results = new JsonArray();
        for (JsonElement el : toolCalls) {
            JsonObject tc = el.getAsJsonObject();
            JsonObject fn = tc.getAsJsonObject("function");
            String name = fn.get("name").getAsString();
            String arguments = fn.has("arguments") && !fn.get("arguments").isJsonNull())
                    ? fn.get("arguments").getAsString()
                    : "{}";
            String result = TOOL_EXECUTOR.execute(name, arguments);
            JsonObject item = new JsonObject();
            item.addProperty("name", name);
            item.addProperty("result", result);
            results.add(item);
        }
        if (results.size() == 1) {
            return results.get(0).getAsJsonObject().get("result").getAsString();
        }
        JsonObject wrapper = new JsonObject();
        wrapper.add("tool_results", results);
        return GSON.toJson(wrapper);
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
