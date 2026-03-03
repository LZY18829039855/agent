package com.agent.housing.logging;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * 按 session_id 分组的请求与工具调用日志。
 * 记录：入站请求、调用的工具及参数、工具响应；同时写入内存（按 session 分组）、控制台和项目根目录下的 logs/ 文件。
 */
public class SessionLogger {

    private static final Gson GSON = new Gson();
    private static final Logger LOG = Logger.getLogger(SessionLogger.class.getName());
    private static final String LOG_PREFIX = "[session_id=%s] ";

    /** 日志文件目录：项目根目录（当前工作目录）下的 logs */
    private static final String LOG_DIR = "logs";
    /** 单文件最大约 5MB，保留 3 个备份，追加写入 */
    private static final int FILE_LIMIT_BYTES = 5 * 1024 * 1024;
    private static final int FILE_COUNT = 3;

    private final ConcurrentHashMap<String, List<SessionLogEntry>> logsBySession = new ConcurrentHashMap<String, List<SessionLogEntry>>();
    /** 每个 session 最多保留的日志条数，避免内存无限增长 */
    private static final int MAX_ENTRIES_PER_SESSION = 500;

    private static final SessionLogger INSTANCE = new SessionLogger();

    static {
        String baseDir = System.getProperty("user.dir");
        File logDir = new File(baseDir, LOG_DIR);
        if (logDir.exists() || logDir.mkdirs()) {
            String pattern = new File(logDir, "housing-agent%g.log").getPath();
            try {
                FileHandler fh = new FileHandler(pattern, FILE_LIMIT_BYTES, FILE_COUNT, true);
                fh.setFormatter(new SimpleFormatter());
                fh.setLevel(Level.INFO);
                LOG.addHandler(fh);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "无法创建日志文件 " + pattern + ": " + e.getMessage());
            }
        }
    }

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

    /**
     * 记录 Agent 返回给用户的 response 字段（无工具时为回复字符串，有工具时为转义 JSON 字符串）。
     *
     * @param sessionId 会话 ID
     * @param response  response 字段的完整内容
     */
    public void logAgentResponse(String sessionId, String response) {
        String sid = sessionId != null ? sessionId : "";
        int len = response != null ? response.length() : 0;
        String summary = String.format("AGENT_RESPONSE length=%d", len);
        String detail = response != null ? response : "";
        appendAndLog(sid, LogType.AGENT_RESPONSE, summary, detail);
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
