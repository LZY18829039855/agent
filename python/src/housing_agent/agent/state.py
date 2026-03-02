"""
基于 session_id 的对话上下文管理，与 Java SessionContextManager 一致。
同一 session_id 多轮请求共享历史；单会话消息数有上限；维护上一轮房源 ID 列表。
"""
from __future__ import annotations

import copy
import threading
from typing import Any

MAX_MESSAGES_PER_SESSION = 30
MAX_HOUSES = 5


class SessionContextManager:
    _instance = None
    _lock = threading.Lock()

    def __init__(self) -> None:
        self._session_messages: dict[str, list[dict[str, Any]]] = {}
        self._session_last_house_ids: dict[str, list[str]] = {}
        self._messages_lock = threading.Lock()
        self._houses_lock = threading.Lock()

    @classmethod
    def get_instance(cls) -> "SessionContextManager":
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = cls()
        return cls._instance

    def _key(self, session_id: str | None) -> str:
        return session_id if session_id else ""

    def get_messages_and_append_user(self, session_id: str | None, user_content: str | None) -> list[dict]:
        """获取当前会话消息列表（副本），并追加本轮用户消息。返回包含历史+本轮 user 的 messages。"""
        key = self._key(session_id)
        with self._messages_lock:
            if key not in self._session_messages:
                self._session_messages[key] = []
            list_ref = self._session_messages[key]
            out = copy.deepcopy(list_ref)
            user_msg = {"role": "user", "content": user_content if user_content is not None else ""}
            list_ref.append(user_msg)
            out.append(user_msg)
            while len(list_ref) > MAX_MESSAGES_PER_SESSION:
                list_ref.pop(0)
        return out

    def append_assistant_and_tool_results(
        self,
        session_id: str | None,
        assistant_message: dict,
        tool_call_ids: list[str],
        tool_results: list[str],
    ) -> None:
        """追加助手消息（含 tool_calls）及对应的 tool 结果消息。"""
        key = self._key(session_id)
        with self._messages_lock:
            if key not in self._session_messages:
                return
            self._session_messages[key].append(copy.deepcopy(assistant_message))
            n = min(len(tool_call_ids), len(tool_results))
            for i in range(n):
                self._session_messages[key].append({
                    "role": "tool",
                    "tool_call_id": tool_call_ids[i],
                    "content": tool_results[i],
                })
            list_ref = self._session_messages[key]
            while len(list_ref) > MAX_MESSAGES_PER_SESSION:
                list_ref.pop(0)

    def append_assistant_message(self, session_id: str | None, assistant_message: dict) -> None:
        """追加助手纯文本回复（无 tool_calls）。"""
        key = self._key(session_id)
        with self._messages_lock:
            if key not in self._session_messages:
                return
            self._session_messages[key].append(copy.deepcopy(assistant_message))
            list_ref = self._session_messages[key]
            while len(list_ref) > MAX_MESSAGES_PER_SESSION:
                list_ref.pop(0)

    def set_last_house_ids(self, session_id: str | None, house_ids: list[str]) -> None:
        """设置该会话本轮的房源 ID 列表（最多保留 MAX_HOUSES 个）。"""
        key = self._key(session_id)
        with self._houses_lock:
            if not house_ids:
                self._session_last_house_ids.pop(key, None)
                return
            self._session_last_house_ids[key] = house_ids[:MAX_HOUSES]

    def get_last_house_ids(self, session_id: str | None) -> list[str]:
        """获取该会话上一轮保存的房源 ID 列表。"""
        key = self._key(session_id)
        with self._houses_lock:
            return list(self._session_last_house_ids.get(key, []))
