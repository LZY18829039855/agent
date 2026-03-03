package com.agent.housing.logging;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 按 session_id 分组的请求与工具调用日志。
 * 记录：入站请求、调用的工具及参数、工具响应；同时写入内存（按 session 分组）和 JDK Logger。
 */
public class SessionLogger {

    private static final Gson GSON = new Gson();
    private static final Logger LOG = Logger.getLogger(SessionLogger.class.getName());
    private static final String LOG_PREFIX = "[session_id=%s] ";

    private final ConcurrentHashMap<String, List<SessionLogEntry>> logsBySession = new ConcurrentHashMap<String, List<SessionLogEntry>>();
    /** 每个 session 最多保留的日志条数，避免内存无限增长 */
    private static final int MAX_ENTRIES_PER_SESSION = 500;

    private static final SessionLogger INSTANCE = new SessionLogger();

    public static SessionLogger getInstance() {
        return INSTANCE;
    }

    private SessionLogger() {
    }

    /**
     * 记录入站聊天请求。
     *
     * @param sessionId 会话 ID
     * @param modelIp   模型 IP/URL
     * @param message   用户消息内容
     */
    public void logRequest(String sessionId, String modelIp, String message) {
        String sid = sessionId != null ? sessionId : "";
        String summary = String.format("REQUEST model_ip=%s message_length=%d", modelIp != null ? modelIp : "", message != null ? message.length() : 0);
        JsonObject detailObj = new JsonObject();
        detailObj.addProperty("model_ip", modelIp != null ? modelIp : "");
        detailObj.addProperty("message", message != null ? message : "");
        String detail = GSON.toJson(detailObj);
        appendAndLog(sid, LogType.REQUEST, summary, detail);
    }

    /**
     * 记录工具调用（调用前）。
     *
     * @param sessionId  会话 ID
     * @param toolName   工具名
     * @param arguments  参数 JSON 字符串
     */
    public void logToolCall(String sessionId, String toolName, String arguments) {
        String sid = sessionId != null ? sessionId : "";
        String summary = String.format("TOOL_CALL name=%s", toolName != null ? toolName : "");
        JsonObject detailObj = new JsonObject();
        detailObj.addProperty("name", toolName != null ? toolName : "");
        detailObj.addProperty("arguments", arguments != null ? arguments : "{}");
        String detail = GSON.toJson(detailObj);
        appendAndLog(sid, LogType.TOOL_CALL, summary, detail);
    }

    /**
     * 记录工具响应（调用后）。
     *
     * @param sessionId 会话 ID
     * @param toolName  工具名
     * @param result    工具返回结果 JSON 字符串
     */
    public void logToolResponse(String sessionId, String toolName, String result) {
        String sid = sessionId != null ? sessionId : "";
        int len = result != null ? result.length() : 0;
        String summary = String.format("TOOL_RESPONSE name=%s result_length=%d", toolName != null ? toolName : "", len);
        JsonObject detailObj = new JsonObject();
        detailObj.addProperty("name", toolName != null ? toolName : "");
        detailObj.addProperty("result", result != null ? result : "");
        String detail = GSON.toJson(detailObj);
        appendAndLog(sid, LogType.TOOL_RESPONSE, summary, detail);
    }

    private void appendAndLog(String sessionId, LogType type, String summary, String detail) {
        long ts = System.currentTimeMillis();
        SessionLogEntry entry = new SessionLogEntry(type, sessionId, ts, summary, detail);

        List<SessionLogEntry> list = logsBySession.get(sessionId);
        if (list == null) {
            list = Collections.synchronizedList(new ArrayList<SessionLogEntry>());
            List<SessionLogEntry> prev = logsBySession.putIfAbsent(sessionId, list);
            if (prev != null) {
                list = prev;
            }
        }
        synchronized (list) {
            list.add(entry);
            while (list.size() > MAX_ENTRIES_PER_SESSION) {
                list.remove(0);
            }
        }

        String prefix = String.format(LOG_PREFIX, sessionId);
        String logLine = prefix + type + " " + summary;
        if (detail != null && !detail.isEmpty() && detail.length() <= 2000) {
            logLine = logLine + " | " + detail;
        } else if (detail != null && detail.length() > 2000) {
            logLine = logLine + " | (detail length=" + detail.length() + ")";
        }
        LOG.log(Level.INFO, logLine);
    }

    /**
     * 按 session_id 获取该会话的日志条目（快照，按时间顺序）。
     *
     * @param sessionId 会话 ID
     * @return 该 session 的日志列表，无则返回空列表
     */
    public List<SessionLogEntry> getLogsBySession(String sessionId) {
        String sid = sessionId != null ? sessionId : "";
        List<SessionLogEntry> list = logsBySession.get(sid);
        if (list == null) {
            return Collections.emptyList();
        }
        synchronized (list) {
            return new ArrayList<SessionLogEntry>(list);
        }
    }

    /**
     * 清除某会话的日志（可选，用于释放内存）。
     */
    public void clearSession(String sessionId) {
        if (sessionId != null) {
            logsBySession.remove(sessionId);
        }
    }
}
