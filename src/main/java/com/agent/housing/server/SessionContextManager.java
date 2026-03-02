package com.agent.housing.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 session_id 的对话上下文管理。
 * 同一 session_id 的多轮请求共享历史消息，请求模型时携带完整上下文；单会话消息数有上限，超出则丢弃最早的消息。
 */
public class SessionContextManager {

    private static final int MAX_MESSAGES_PER_SESSION = 30;

    private final ConcurrentHashMap<String, List<JsonObject>> sessionMessages = new ConcurrentHashMap<String, List<JsonObject>>();

    /**
     * 获取当前会话的消息列表（副本），并追加本轮用户消息。
     *
     * @param sessionId 会话 ID，可为空（当作空串处理）
     * @param userContent 本轮用户输入
     * @return 包含历史 + 本轮 user 的 messages，用于请求模型
     */
    public JsonArray getMessagesAndAppendUser(String sessionId, String userContent) {
        String key = sessionId != null ? sessionId : "";
        List<JsonObject> list = sessionMessages.get(key);
        if (list == null) {
            list = Collections.synchronizedList(new ArrayList<JsonObject>());
            List<JsonObject> prev = sessionMessages.putIfAbsent(key, list);
            if (prev != null) {
                list = prev;
            }
        }
        JsonArray out = new JsonArray();
        synchronized (list) {
            for (JsonObject m : list) {
                out.add(m.deepCopy());
            }
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userContent != null ? userContent : "");
            list.add(userMsg);
            out.add(userMsg);
            trimToMax(list);
        }
        return out;
    }

    /**
     * 追加助手消息（含 tool_calls）及对应的 tool 结果消息。
     *
     * @param sessionId 会话 ID
     * @param assistantMessage 模型返回的 message（含 role、content、tool_calls）
     * @param toolCallIds 与 tool_calls 顺序一致的 id 列表
     * @param toolResults 与 tool_calls 顺序一致的结果字符串列表
     */
    public void appendAssistantAndToolResults(String sessionId, JsonObject assistantMessage,
                                              List<String> toolCallIds, List<String> toolResults) {
        String key = sessionId != null ? sessionId : "";
        List<JsonObject> list = sessionMessages.get(key);
        if (list == null) {
            return;
        }
        synchronized (list) {
            list.add((JsonObject) assistantMessage.deepCopy());
            if (toolCallIds != null && toolResults != null) {
                int n = Math.min(toolCallIds.size(), toolResults.size());
                for (int i = 0; i < n; i++) {
                    JsonObject toolMsg = new JsonObject();
                    toolMsg.addProperty("role", "tool");
                    toolMsg.addProperty("tool_call_id", toolCallIds.get(i));
                    toolMsg.addProperty("content", toolResults.get(i));
                    list.add(toolMsg);
                }
            }
            trimToMax(list);
        }
    }

    /**
     * 追加助手纯文本回复（无 tool_calls）。
     */
    public void appendAssistantMessage(String sessionId, JsonObject assistantMessage) {
        String key = sessionId != null ? sessionId : "";
        List<JsonObject> list = sessionMessages.get(key);
        if (list == null) {
            return;
        }
        synchronized (list) {
            list.add((JsonObject) assistantMessage.deepCopy());
            trimToMax(list);
        }
    }

    private void trimToMax(List<JsonObject> list) {
        while (list.size() > MAX_MESSAGES_PER_SESSION) {
            list.remove(0);
        }
    }

    /** 单例，供 ChatHandler 使用 */
    private static final SessionContextManager INSTANCE = new SessionContextManager();

    public static SessionContextManager getInstance() {
        return INSTANCE;
    }
}
