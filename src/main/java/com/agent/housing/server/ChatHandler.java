package com.agent.housing.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Collections;

/**
 * 处理 POST /api/v1/chat 请求：解析 model_ip、session_id、message，
 * 调用模型接口（model_ip:8888/v1/chat/completions，请求头 Session-ID），并返回模型响应。
 */
public class ChatHandler implements com.sun.net.httpserver.HttpHandler {

    private static final Gson GSON = new Gson();
    private static final String UTF8 = "UTF-8";

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

        exchange.getResponseHeaders().put("Content-Type", Collections.singletonList("application/json; charset=UTF-8"));
        byte[] bytes = modelResponse.getBytes(UTF8);
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
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
