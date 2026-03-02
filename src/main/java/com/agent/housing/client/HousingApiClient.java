package com.agent.housing.client;

import com.agent.housing.config.AgentConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 租房仿真 API 的 HTTP 客户端（Java 8 兼容）。
 * 房源相关请求自动添加 X-User-ID；地标相关不添加。
 */
public class HousingApiClient {

    private static final String UTF8 = "UTF-8";
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final AgentConfig config;

    public HousingApiClient(AgentConfig config) {
        this.config = config;
    }

    /**
     * GET 请求。path 为相对路径如 /api/landmarks，needUserId 为 true 时添加 X-User-ID。
     */
    public String get(String path, Map<String, String> queryParams, boolean needUserId) throws Exception {
        String urlStr = buildUrl(path, queryParams);
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            if (needUserId && config.getUserId() != null && !config.getUserId().isEmpty()) {
                conn.setRequestProperty("X-User-ID", config.getUserId());
            }
            int code = conn.getResponseCode();
            String body = readBody(conn);
            if (code >= 400) {
                throw new RuntimeException("API 请求失败: " + code + " " + body);
            }
            return body;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * POST 请求。用于 init、rent、terminate、offline。房源相关需 needUserId=true。
     */
    public String post(String path, Map<String, String> queryParams, boolean needUserId) throws Exception {
        String urlStr = buildUrl(path, queryParams);
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            if (needUserId && config.getUserId() != null && !config.getUserId().isEmpty()) {
                conn.setRequestProperty("X-User-ID", config.getUserId());
            }
            conn.getOutputStream().close();
            int code = conn.getResponseCode();
            String body = readBody(conn);
            if (code >= 400) {
                throw new RuntimeException("API 请求失败: " + code + " " + body);
            }
            return body;
        } finally {
            conn.disconnect();
        }
    }

    private String readBody(HttpURLConnection conn) throws Exception {
        BufferedReader reader;
        if (conn.getResponseCode() >= 400) {
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

    private String buildUrl(String path, Map<String, String> queryParams) throws Exception {
        String base = config.getBaseUrl().replaceAll("/$", "");
        String pathNorm = path.startsWith("/") ? path : "/" + path;
        if (queryParams == null || queryParams.isEmpty()) {
            return base + pathNorm;
        }
        StringBuilder q = new StringBuilder();
        for (Map.Entry<String, String> e : queryParams.entrySet()) {
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                if (q.length() > 0) {
                    q.append("&");
                }
                q.append(encode(e.getKey())).append("=").append(encode(e.getValue()));
            }
        }
        return base + pathNorm + "?" + q.toString();
    }

    private static String encode(String s) throws Exception {
        return URLEncoder.encode(s, UTF8);
    }
}
