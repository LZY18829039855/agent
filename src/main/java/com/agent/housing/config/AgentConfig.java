package com.agent.housing.config;

/**
 * Agent 大赛仿真 API 配置。
 * baseUrl：租房仿真接口地址，如 http://7.225.29.223:8080
 * userId：比赛平台注册的用户工号，用于请求头 X-User-ID（房源相关接口必带）。
 * systemPrompt：发给大模型的系统提示词，用于约束 Agent 身份与行为，可选。
 * 可通过系统属性 housing.api.baseUrl、housing.api.userId、housing.agent.systemPrompt 覆盖默认值。
 */
public class AgentConfig {

    /** 租房仿真 API 默认地址 */
    private static final String DEFAULT_BASE_URL = "http://7.225.29.223:8080";
    /** 默认工号，房源类接口请求头 X-User-ID 使用 */
    private static final String DEFAULT_USER_ID = "l00845559";

    private String baseUrl;
    private String userId;
    /** 系统提示词，非空时在请求模型的 messages 最前面插入一条 role=system 的消息 */
    private String systemPrompt;

    public AgentConfig() {
        this.baseUrl = System.getProperty("housing.api.baseUrl", DEFAULT_BASE_URL);
        this.userId = System.getProperty("housing.api.userId", DEFAULT_USER_ID);
        this.systemPrompt = System.getProperty("housing.agent.systemPrompt", "");
    }

    public AgentConfig(String baseUrl, String userId) {
        this.baseUrl = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : DEFAULT_BASE_URL;
        this.userId = (userId != null && !userId.isEmpty()) ? userId : DEFAULT_USER_ID;
        this.systemPrompt = System.getProperty("housing.agent.systemPrompt", "");
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

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
