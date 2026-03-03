package com.agent.housing.server;

import com.agent.housing.tools.ToolDefinitions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * 调用模型接口：IP:8888，路径 /v1/chat/completions，请求头 Session-ID，请求体 model/messages/tools/stream。
 * 支持失败重试与较长超时时间。
 */
public class ModelApiClient {

    private static final Gson GSON = new Gson();
    private static final String UTF8 = "UTF-8";
    /** 连接超时 60 秒 */
    private static final int CONNECT_TIMEOUT_MS = 60000;
    /** 读取超时 5 分钟（模型推理可能较慢） */
    private static final int READ_TIMEOUT_MS = 300000;
    /** 最大重试次数（不含首次），即最多共请求 3 次 */
    private static final int MAX_RETRIES = 2;
    /** 重试前等待毫秒数 */
    private static final int RETRY_DELAY_MS = 2000;

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
     * 若 systemPrompt 非空，会在 messages 最前面插入一条 role=system 的消息。
     * 超时或 5xx/网络异常时会自动重试，最多共请求 3 次。
     *
     * @param modelIp     用户输入的 model_ip（IP 或带协议的 URL）
     * @param sessionId   请求头 Session-ID 的值
     * @param messages    完整 messages（历史 + 本轮 user），可为 null 则仅发一条 user 空内容
     * @param systemPrompt 可选系统提示词，为 null 或空串则不插入 system 消息
     * @return 模型返回的完整 JSON 字符串
     */
    public static String chatWithMessages(String modelIp, String sessionId, JsonArray messages, String systemPrompt) throws Exception {
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
        JsonArray messagesToSend = messages;
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            messagesToSend = new JsonArray();
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", systemPrompt.trim());
            messagesToSend.add(systemMsg);
            for (JsonElement el : messages) {
                messagesToSend.add(el);
            }
        }
        body.add("messages", messagesToSend);

        List<Map<String, Object>> tools = ToolDefinitions.getOpenAIFormatTools();
        body.add("tools", GSON.toJsonTree(tools).getAsJsonArray());
        body.addProperty("stream", false);

        byte[] out = GSON.toJson(body).getBytes(UTF8);
        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试被中断", e);
                }
            }
            try {
                return doOneRequest(urlStr, sessionId, out);
            } catch (Exception e) {
                lastException = e;
                if (!isRetryable(e) || attempt == MAX_RETRIES) {
                    throw e;
                }
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new RuntimeException("模型接口请求失败");
    }

    /** 是否可重试：超时、5xx、连接/IO 异常视为可重试；4xx 不重试 */
    private static boolean isRetryable(Exception e) {
        if (e instanceof SocketTimeoutException) {
            return true;
        }
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
            if (cause instanceof java.net.ConnectException || cause instanceof java.io.IOException) {
                return true;
            }
            cause = cause.getCause();
        }
        String msg = e.getMessage();
        if (msg != null && msg.contains("模型接口请求失败:")) {
            int code = parseHttpCode(msg);
            return code >= 500 || code < 0;
        }
        return true;
    }

    private static int parseHttpCode(String msg) {
        int idx = msg.indexOf("模型接口请求失败: ");
        if (idx < 0) {
            return -1;
        }
        idx += "模型接口请求失败: ".length();
        int end = msg.indexOf(" ", idx);
        if (end < 0) {
            end = msg.length();
        }
        try {
            return Integer.parseInt(msg.substring(idx, end).trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String doOneRequest(String urlStr, String sessionId, byte[] bodyBytes) throws Exception {
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
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            OutputStream os = conn.getOutputStream();
            os.write(bodyBytes);
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
