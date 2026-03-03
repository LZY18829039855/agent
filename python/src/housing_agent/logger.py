"""
按 session_id 分块的请求与工具调用日志，写入当前工作目录下的 logs 目录。
"""
from __future__ import annotations

import json
import logging
import os
import threading
from datetime import datetime

# 日志目录：当前工作目录下的 logs
LOG_DIR = os.path.join(os.getcwd(), "logs")
# 单条结果最大长度，超出截断
MAX_RESULT_LEN = 2000

_lock = threading.Lock()
_file_handler: logging.FileHandler | None = None
_logger: logging.Logger | None = None


def _ensure_logger() -> logging.Logger:
    global _logger, _file_handler
    with _lock:
        if _logger is not None:
            return _logger
        os.makedirs(LOG_DIR, exist_ok=True)
        today = datetime.now().strftime("%Y-%m-%d")
        log_file = os.path.join(LOG_DIR, f"housing_agent_{today}.log")
        _file_handler = logging.FileHandler(log_file, encoding="utf-8")
        _file_handler.setFormatter(
            logging.Formatter("%(asctime)s %(message)s", datefmt="%Y-%m-%d %H:%M:%S")
        )
        _logger = logging.getLogger("housing_agent.request")
        _logger.setLevel(logging.INFO)
        _logger.addHandler(_file_handler)
        _logger.propagate = False
    return _logger


def _safe_str(obj: object, max_len: int | None = None) -> str:
    if obj is None:
        return ""
    try:
        if isinstance(obj, (dict, list)):
            s = json.dumps(obj, ensure_ascii=False)
        else:
            s = str(obj)
    except (TypeError, ValueError):
        s = repr(obj)
    if max_len is not None and len(s) > max_len:
        s = s[:max_len] + "...[truncated]"
    return s


def log_request(session_id: str, model_ip: str, message: str, body: dict | None = None) -> None:
    """记录收到的聊天请求（按 session_id 分块）。"""
    try:
        log = _ensure_logger()
        sid = session_id or "(empty)"
        log.info("[session_id=%s] ======== REQUEST ========", sid)
        log.info("[session_id=%s] model_ip=%s message=%s", sid, model_ip, _safe_str(message, 500))
        if body:
            log.info("[session_id=%s] body: %s", sid, _safe_str(body, 1000))
    except Exception:
        pass


def log_tool_call(session_id: str, tool_name: str, arguments: str) -> None:
    """记录即将调用的工具名与参数。"""
    try:
        log = _ensure_logger()
        sid = session_id or "(empty)"
        log.info("[session_id=%s] TOOL_CALL: %s | args: %s", sid, tool_name, _safe_str(arguments, 500))
    except Exception:
        pass


def log_tool_result(session_id: str, tool_name: str, result: str) -> None:
    """记录工具返回结果（过长时截断）。"""
    try:
        log = _ensure_logger()
        sid = session_id or "(empty)"
        out = _safe_str(result, MAX_RESULT_LEN)
        log.info("[session_id=%s] TOOL_RESULT: %s | %s", sid, tool_name, out)
    except Exception:
        pass


def log_response_end(session_id: str, status: str, duration_ms: int) -> None:
    """记录本轮响应结束（status、耗时）。"""
    try:
        log = _ensure_logger()
        sid = session_id or "(empty)"
        log.info("[session_id=%s] ======== RESPONSE END status=%s duration_ms=%s ========", sid, status, duration_ms)
    except Exception:
        pass
