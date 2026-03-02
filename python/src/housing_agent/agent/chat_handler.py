"""
处理 POST /api/v1/chat：解析 model_ip、session_id、message，调用模型；
若模型返回 tool_calls 则执行工具，从工具响应的 data.items 解析 house_id；
返回格式与 Java ChatHandler 一致。
"""
import json
import time

from housing_agent.agent.model_client import chat_with_messages
from housing_agent.agent.state import SessionContextManager
from housing_agent.config import get_system_prompt
from housing_agent.tools.executor import ToolExecutor

MESSAGE_HOUSES_FOUND = "为您找到以下符合条件的房源："
MESSAGE_NO_HOUSES = "未找到符合条件的房源。"
MAX_HOUSES_IN_RESPONSE = 5


def _collect_house_ids_from_result(result_json: str) -> list[str]:
    """从接口响应 JSON（code、message、data.items[].house_id）中解析出所有 house_id。"""
    if not result_json or not result_json.strip():
        return []
    try:
        root = json.loads(result_json)
        data = root.get("data")
        if not data:
            return []
        items = data.get("items")
        if not items or not isinstance(items, list):
            return []
        ids = []
        for o in items:
            if isinstance(o, dict) and "house_id" in o:
                ids.append(str(o["house_id"]))
        return ids
    except (json.JSONDecodeError, TypeError):
        return []


def handle_chat(
    model_ip: str,
    session_id: str | None,
    message: str | None,
) -> dict:
    """
    处理一轮聊天：取会话消息并追加 user，调模型，若有 tool_calls 则执行并写回会话，
    返回与 Java 一致的 body：session_id, response{ message, houses }, status, tool_results, timestamp, duration_ms。
    """
    start_ms = int(time.time() * 1000)
    session_id = session_id if session_id is not None else ""
    message = message if message is not None else ""

    ctx = SessionContextManager.get_instance()
    system_prompt = get_system_prompt()
    executor = ToolExecutor()

    messages = ctx.get_messages_and_append_user(session_id, message)
    try:
        model_response = chat_with_messages(model_ip, session_id, messages, system_prompt)
    except Exception as e:
        return {
            "session_id": session_id,
            "response": {"message": "", "houses": []},
            "status": "error",
            "error": str(e),
            "tool_results": [],
            "timestamp": start_ms // 1000,
            "duration_ms": int(time.time() * 1000) - start_ms,
        }

    tool_results = []
    response_content = {"message": "", "houses": []}
    all_house_ids: list[str] = []

    try:
        root = json.loads(model_response)
        choices = root.get("choices") if root else None
        if not choices or len(choices) == 0:
            response_content["houses"] = ctx.get_last_house_ids(session_id)
            return _build_response(session_id, response_content, tool_results, start_ms)
        msg = choices[0].get("message") if isinstance(choices[0], dict) else None
        if not msg:
            response_content["houses"] = ctx.get_last_house_ids(session_id)
            return _build_response(session_id, response_content, tool_results, start_ms)

        tool_calls = msg.get("tool_calls")
        if tool_calls and len(tool_calls) > 0:
            tool_call_ids = []
            result_strings = []
            for tc in tool_calls:
                fn = tc.get("function") if isinstance(tc, dict) else {}
                if not isinstance(fn, dict):
                    continue
                name = fn.get("name")
                args_str = fn.get("arguments") or "{}"
                try:
                    result = executor.execute_json(name, args_str)
                except Exception as e:
                    result = json.dumps({"code": -1, "message": str(e)})
                tool_results.append({"name": name, "result": result})
                tool_call_ids.append(tc.get("id") or "")
                result_strings.append(result)
                all_house_ids.extend(_collect_house_ids_from_result(result))
            response_content["message"] = MESSAGE_HOUSES_FOUND if all_house_ids else MESSAGE_NO_HOUSES
            response_content["houses"] = all_house_ids[:MAX_HOUSES_IN_RESPONSE]
            ctx.set_last_house_ids(session_id, all_house_ids)
            ctx.append_assistant_and_tool_results(session_id, msg, tool_call_ids, result_strings)
        else:
            content = msg.get("content")
            response_content["message"] = content if content is not None else ""
            response_content["houses"] = ctx.get_last_house_ids(session_id)
            ctx.append_assistant_message(session_id, msg)
    except (json.JSONDecodeError, TypeError, KeyError):
        response_content["houses"] = ctx.get_last_house_ids(session_id)

    return _build_response(session_id, response_content, tool_results, start_ms)


def _build_response(
    session_id: str,
    response_content: dict,
    tool_results: list[dict],
    start_ms: int,
) -> dict:
    duration_ms = int(time.time() * 1000) - start_ms
    return {
        "session_id": session_id,
        "response": response_content,
        "status": "success",
        "tool_results": tool_results,
        "timestamp": start_ms // 1000,
        "duration_ms": duration_ms,
    }
