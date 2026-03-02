package com.agent.housing.server;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

/**
 * 本地 HTTP 服务，端口 8191，提供 Agent 启动接口 POST /api/v1/chat。
 */
public class AgentServer {

    public static final int PORT = 8191;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/v1/chat", new ChatHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Agent 服务已启动: http://localhost:" + PORT);
        System.out.println("  POST /api/v1/chat  请求体: { \"model_ip\", \"session_id\", \"message\" }");
    }
}
