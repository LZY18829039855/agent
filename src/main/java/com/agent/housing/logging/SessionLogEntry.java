package com.agent.housing.logging;

/**
 * 单条会话日志条目，按 session_id 分组存储。
 */
public class SessionLogEntry {

    private final LogType type;
    private final String sessionId;
    private final long timestampMs;
    private final String summary;
    private final String detail;

    public SessionLogEntry(LogType type, String sessionId, long timestampMs, String summary, String detail) {
        this.type = type;
        this.sessionId = sessionId != null ? sessionId : "";
        this.timestampMs = timestampMs;
        this.summary = summary != null ? summary : "";
        this.detail = detail != null ? detail : "";
    }

    public LogType getType() {
        return type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return String.format("[%s][%s][%s] %s", sessionId, type, timestampMs, summary);
    }
}
