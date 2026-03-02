package com.agent.housing.server;

import com.agent.housing.tools.ToolDefinitions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 调用模型接口：IP:8888，路径 /v1/chat/completions，请求头 Session-ID，请求体 model/messages/tools/stream。
 */
public class ModelApiClient {

    private static final Gson GSON = new Gson();
    private static final String UTF8 = "UTF-8";
    private static final int CONNECT_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 120000;

    /**
     * 根据用户输入的 model_ip 拼出模型 base URL（端口固定 8888）。
     * 支持 model_ip 为 "127.0.0.1" 或 "http://127.0.0.1" 等形式。
     */
    public static String buildModelBaseUrl(String modelIp) {
        if (modelIp == null || modelIp.trim().isEmpty()) {
            throw new IllegalArgumentException("model_ip 不能为空");
        }
        String s = modelIp.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) {
            // 去掉末尾斜杠，去掉已有端口后追加 :8888
            String noSlash = s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
            int portIdx = noSlash.lastIndexOf(':');
            if (portIdx > 0 && portIdx > noSlash.indexOf("://") + 2) {
                try {
                    Integer.parseInt(noSlash.substring(portIdx + 1));
                    return noSlash.substring(0, portIdx) + ":8888";
                } catch (NumberFormatException ignored) {
                }
            }
            return noSlash + ":8888";
        }
        return "http://" + s + ":8888";
    }

    /**
     * 调用模型 /v1/chat/completions，携带完整对话历史（多轮上下文）。
     *
     * @param modelIp   用户输入的 model_ip（IP 或带协议的 URL）
     * @param sessionId 请求头 Session-ID 的值
     * @param messages  完整 messages（历史 + 本轮 user），可为 null 则仅发一条 user 空内容
     * @return 模型返回的完整 JSON 字符串
     */
    public static String chatWithMessages(String modelIp, String sessionId, JsonArray messages) throws Exception {
        String baseUrl = buildModelBaseUrl(modelIp);
        String urlStr = baseUrl + "/v1/chat/completions";

        JsonObject body = new JsonObject();
        body.addProperty("model", "");
        if (messages == null || messages.size() == 0) {
            messages = new JsonArray();
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", "");
            messages.add(userMsg);
        }
        body.add("messages", messages);

        List<Map<String, Object>> tools = ToolDefinitions.getOpenAIFormatTools();
        body.add("tools", GSON.toJsonTree(tools).getAsJsonArray());
        body.addProperty("stream", false);

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            if (sessionId != null && !sessionId.isEmpty()) {
                conn.setRequestProperty("Session-ID", sessionId);
            }

            byte[] out = GSON.toJson(body).getBytes(UTF8);
            conn.setRequestProperty("Content-Length", String.valueOf(out.length));
            OutputStream os = conn.getOutputStream();
            os.write(out);
            os.close();

            int code = conn.getResponseCode();
            String responseBody = readBody(conn);
            if (code >= 400) {
                throw new RuntimeException("模型接口请求失败: " + code + " " + responseBody);
            }
            return responseBody;
        } finally {
            conn.disconnect();
        }
    }

    private static String readBody(HttpURLConnection conn) throws Exception {
        BufferedReader reader;
        if (conn.getResponseCode() >= 400 && conn.getErrorStream() != null) {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), UTF8));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), UTF8));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}
