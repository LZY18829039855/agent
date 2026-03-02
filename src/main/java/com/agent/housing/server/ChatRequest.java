package com.agent.housing.server;

/**
 * Agent 启动/聊天接口的请求参数。
 */
public class ChatRequest {

    private String model_ip;
    private String session_id;
    private String message;

    public ChatRequest() {
    }

    public String getModel_ip() {
        return model_ip;
    }

    public void setModel_ip(String model_ip) {
        this.model_ip = model_ip;
    }

    public String getSession_id() {
        return session_id;
    }

    public void setSession_id(String session_id) {
        this.session_id = session_id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
