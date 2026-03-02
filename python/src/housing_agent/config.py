"""
租房 Agent 配置：从环境变量读取，与 Java AgentConfig 语义一致。
"""
import os

# 租房仿真 API
DEFAULT_BASE_URL = "http://7.225.29.223:8080"
DEFAULT_USER_ID = "l00845559"

# 模型默认（请求里可传 model_ip 覆盖）
DEFAULT_MODEL_BASE_URL = "http://127.0.0.1:8888"


def get_housing_base_url() -> str:
    return os.environ.get("HOUSING_API_BASE_URL", DEFAULT_BASE_URL).rstrip("/")


def get_housing_user_id() -> str:
    return os.environ.get("HOUSING_API_USER_ID", DEFAULT_USER_ID)


def get_system_prompt() -> str:
    from housing_agent.agent.prompts import DEFAULT_SYSTEM_PROMPT
    return os.environ.get("HOUSING_AGENT_SYSTEM_PROMPT", DEFAULT_SYSTEM_PROMPT)


def build_model_base_url(model_ip: str | None) -> str:
    """根据 model_ip 拼出模型 base URL（端口固定 8888），与 Java ModelApiClient 一致。"""
    if not model_ip or not str(model_ip).strip():
        return DEFAULT_MODEL_BASE_URL.rstrip("/")
    s = str(model_ip).strip()
    if s.startswith("http://") or s.startswith("https://"):
        no_slash = s.rstrip("/")
        idx = no_slash.rfind(":")
        if idx > 0 and idx > no_slash.find("://") + 2:
            try:
                int(no_slash[idx + 1 :])
                return no_slash[:idx] + ":8888"
            except ValueError:
                pass
        return no_slash + ":8888"
    return "http://" + s + ":8888"
