"""
调用模型接口：base_url + /v1/chat/completions，使用 LangChain ChatOpenAI 封装，
与 Java ModelApiClient 行为一致（Session-ID、messages、tools、stream=false）。
"""
from __future__ import annotations

import json

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
    ToolMessage,
)
from langchain_openai import ChatOpenAI

from housing_agent.config import build_model_base_url
from housing_agent.tools.executor import ToolExecutor
from housing_agent.tools.schemas import get_openai_format_tools

CONNECT_TIMEOUT = 30
READ_TIMEOUT = 120


def _dict_to_langchain_message(m: dict) -> BaseMessage:
    """将 OpenAI 格式的 message 转为 LangChain BaseMessage。"""
    role = (m.get("role") or "").strip().lower()
    content = m.get("content")
    if content is None:
        content = ""
    if role == "system":
        return SystemMessage(content=content)
    if role == "user":
        return HumanMessage(content=content)
    if role == "assistant":
        tool_calls = m.get("tool_calls")
        if tool_calls:
            return AIMessage(
                content=content or "",
                tool_calls=[
                    {
                        "id": tc.get("id") or "",
                        "name": (tc.get("function") or {}).get("name") or "",
                        "args": json.loads((tc.get("function") or {}).get("arguments") or "{}"),
                    }
                    for tc in tool_calls
                ],
            )
        return AIMessage(content=content or "")
    if role == "tool":
        return ToolMessage(
            content=m.get("content") or "",
            tool_call_id=m.get("tool_call_id") or "",
        )
    return HumanMessage(content=str(content))


def _tc_id(tc: object) -> str:
    return getattr(tc, "id", None) or (tc.get("id") if isinstance(tc, dict) else None) or ""


def _tc_name(tc: object) -> str:
    return getattr(tc, "name", None) or (tc.get("name") if isinstance(tc, dict) else None) or ""


def _tc_args(tc: object) -> dict:
    args = getattr(tc, "args", None) or (tc.get("args") if isinstance(tc, dict) else None)
    return args if isinstance(args, dict) else {}


def _ai_message_to_response_json(ai: AIMessage) -> str:
    """将 LangChain AIMessage 转成与模型 API 一致的 JSON 字符串，供 chat_handler 解析。"""
    msg = {"role": "assistant", "content": ai.content or None}
    if getattr(ai, "tool_calls", None):
        msg["tool_calls"] = [
            {
                "id": _tc_id(tc),
                "type": "function",
                "function": {
                    "name": _tc_name(tc),
                    "arguments": json.dumps(_tc_args(tc), ensure_ascii=False),
                },
            }
            for tc in (ai.tool_calls or [])
        ]
    return json.dumps({"choices": [{"message": msg}]}, ensure_ascii=False)


def _make_tool_run(executor: ToolExecutor, tool_name: str):
    """闭包：绑定 executor 与 tool_name，避免循环中 name 被覆盖。"""
    def _run(**kwargs: object) -> str:
        return executor.execute(tool_name, kwargs)
    return _run


def _build_langchain_tools():
    """从 16 个 OpenAI 格式 schema + ToolExecutor 构建 LangChain 可绑定工具列表。"""
    from langchain_core.tools import StructuredTool
    from pydantic import BaseModel, ConfigDict

    class AnyArgs(BaseModel):
        model_config = ConfigDict(extra="allow")

    executor = ToolExecutor()
    tools = []
    for t in get_openai_format_tools():
        fn = t.get("function") or {}
        name = fn.get("name") or ""
        desc = fn.get("description") or ""
        tools.append(
            StructuredTool.from_function(
                func=_make_tool_run(executor, name),
                name=name,
                description=desc,
                args_schema=AnyArgs,
            ),
        )
    return tools


def chat_with_messages(
    model_ip: str | None,
    session_id: str | None,
    messages: list[dict],
    system_prompt: str | None,
) -> str:
    """
    调用模型 /v1/chat/completions，携带完整对话历史。
    使用 LangChain ChatOpenAI（base_url）+ bind_tools，与 Java 行为一致。
    返回模型返回格式的 JSON 字符串（choices[0].message 含 content / tool_calls）。
    """
    base_url = build_model_base_url(model_ip).rstrip("/")
    if not messages:
        messages = [{"role": "user", "content": ""}]
    if system_prompt and system_prompt.strip():
        messages = [{"role": "system", "content": system_prompt.strip()}] + list(messages)

    lc_messages: list[BaseMessage] = [_dict_to_langchain_message(m) for m in messages]
    tools = _build_langchain_tools()
    model = ChatOpenAI(
        base_url=base_url + "/v1",
        api_key="not-needed",
        model="",
        request_timeout=READ_TIMEOUT,
    ).bind_tools(tools)
    ai: AIMessage = model.invoke(lc_messages)
    return _ai_message_to_response_json(ai)
