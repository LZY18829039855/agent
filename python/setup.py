"""
兼容 pip install -e . 的可编辑安装（与 pyproject.toml 依赖保持一致）。
"""
from setuptools import setup, find_packages

setup(
    name="housing-agent",
    version="0.1.0",
    description="租房 Agent 服务（Flask + LangChain）",
    packages=find_packages(where="src"),
    package_dir={"": "src"},
    install_requires=[
        "flask>=2.3.0",
        "requests>=2.28.0",
        "langchain>=0.2.0",
        "langchain-openai>=0.0.5",
        "langchain-core>=0.2.0",
        "langchain-community>=0.0.10",
    ],
    python_requires=">=3.8",
)
