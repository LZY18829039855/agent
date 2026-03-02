package com.agent.housing.config;

/**
 * Agent 大赛仿真 API 配置。
 * baseUrl 示例：http://IP:8080（将 IP 换为比赛给定的黄区/绿区 IP）
 * userId 为比赛平台注册的用户工号，用于请求头 X-User-ID。
 */
public class AgentConfig {

    private String baseUrl = "http://localhost:8080";
    private String userId = "";

    public AgentConfig() {
    }

    public AgentConfig(String baseUrl, String userId) {
        this.baseUrl = baseUrl != null ? baseUrl : this.baseUrl;
        this.userId = userId != null ? userId : this.userId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
