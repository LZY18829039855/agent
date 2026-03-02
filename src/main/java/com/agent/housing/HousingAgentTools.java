package com.agent.housing;

import com.agent.housing.config.AgentConfig;
import com.agent.housing.tools.ToolDefinitions;
import com.agent.housing.tools.ToolExecutor;

import java.util.List;
import java.util.Map;

/**
 * Agent 大赛租房仿真 Tool 入口。
 * <ul>
 *   <li>获取 LLM 所需的 tool 列表：{@link #getToolsForLLM()}</li>
 *   <li>执行工具：{@link #execute(String, Map)} / {@link #execute(String, String)}</li>
 * </ul>
 */
public class HousingAgentTools {

    private final AgentConfig config;
    private final ToolExecutor executor;

    public HousingAgentTools(AgentConfig config) {
        this.config = config;
        this.executor = new ToolExecutor(config);
    }

    public HousingAgentTools(String baseUrl, String userId) {
        this(new AgentConfig(baseUrl, userId));
    }

    /**
     * 返回 OpenAI 兼容格式的 tools 列表，可直接传给大模型 API 的 tools 参数。
     */
    public List<Map<String, Object>> getToolsForLLM() {
        return ToolDefinitions.getOpenAIFormatTools();
    }

    /**
     * 执行指定工具，参数为 Map（例如从 LLM 返回的 arguments 解析而来）。
     *
     * @return API 返回的 JSON 字符串
     */
    public String execute(String toolName, Map<String, Object> arguments) throws Exception {
        return executor.execute(toolName, arguments);
    }

    /**
     * 执行指定工具，参数为 JSON 字符串（LLM 返回的 arguments 原文）。
     *
     * @return API 返回的 JSON 字符串
     */
    public String execute(String toolName, String argumentsJson) throws Exception {
        return executor.execute(toolName, argumentsJson);
    }

    public AgentConfig getConfig() {
        return config;
    }
}
