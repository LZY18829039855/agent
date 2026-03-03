package com.agent.housing.logging;

/**
 * 会话日志条目类型：请求、工具调用、工具响应。
 */
public enum LogType {
    /** 入站聊天请求 */
    REQUEST,
    /** 调用的工具（名称与参数） */
    TOOL_CALL,
    /** 工具执行结果 */
    TOOL_RESPONSE
}
