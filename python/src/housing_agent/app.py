"""
Flask 应用入口：/health、POST /api/v1/chat，与 Java Agent 输入输出格式一致。
默认端口 8191，与 Java 服务一致。
"""
import os

from flask import Flask, request, jsonify

from housing_agent.agent.chat_handler import handle_chat

# 默认端口 8191，使用 flask run 时生效
if "FLASK_RUN_PORT" not in os.environ:
    os.environ["FLASK_RUN_PORT"] = "8191"

app = Flask(__name__)


@app.route("/health")
def health():
    return jsonify({"status": "ok", "service": "housing-agent-python"})


@app.route("/api/v1/chat", methods=["POST"])
def chat():
    if request.method != "POST":
        return jsonify({"error": "仅支持 POST"}), 405
    try:
        body = request.get_json(force=True, silent=True) or {}
    except Exception as e:
        return jsonify({"error": f"请求体 JSON 解析失败: {e}"}), 400
    model_ip = body.get("model_ip") or (request.headers.get("model_ip") or "").strip()
    session_id = body.get("session_id")
    message = body.get("message")
    if not model_ip:
        return jsonify({"error": "model_ip 不能为空"}), 400
    result = handle_chat(model_ip=model_ip, session_id=session_id, message=message)
    if result.get("status") == "error":
        return jsonify(result), 502
    return jsonify(result), 200
