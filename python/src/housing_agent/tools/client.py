"""
租房仿真 API 的 HTTP 客户端。房源相关请求自动添加 X-User-ID，与 Java HousingApiClient 一致。
"""
import urllib.parse
import requests

CONNECT_TIMEOUT = 10
READ_TIMEOUT = 30


class HousingApiClient:
    def __init__(self, base_url: str, user_id: str):
        self.base_url = base_url.rstrip("/")
        self.user_id = user_id or ""

    def _url(self, path: str, query_params: dict) -> str:
        path = path if path.startswith("/") else "/" + path
        if not query_params:
            return self.base_url + path
        q = "&".join(
            f"{urllib.parse.quote(k)}={urllib.parse.quote(str(v))}"
            for k, v in query_params.items()
            if v is not None and str(v).strip() != ""
        )
        return self.base_url + path + "?" + q

    def _headers(self, need_user_id: bool) -> dict:
        headers = {}
        if need_user_id and self.user_id:
            headers["X-User-ID"] = self.user_id
        return headers

    def get(self, path: str, query_params: dict | None, need_user_id: bool) -> str:
        params = query_params or {}
        url = self._url(path, params)
        resp = requests.get(
            url,
            headers=self._headers(need_user_id),
            timeout=(CONNECT_TIMEOUT, READ_TIMEOUT),
        )
        if resp.status_code >= 400:
            raise RuntimeError(f"API 请求失败: {resp.status_code} {resp.text}")
        return resp.text

    def post(self, path: str, query_params: dict | None, need_user_id: bool) -> str:
        """POST：Java 侧参数放在 URL query 中，body 为空。"""
        params = query_params or {}
        url = self._url(path, params)
        resp = requests.post(
            url,
            headers=self._headers(need_user_id),
            timeout=(CONNECT_TIMEOUT, READ_TIMEOUT),
        )
        if resp.status_code >= 400:
            raise RuntimeError(f"API 请求失败: {resp.status_code} {resp.text}")
        return resp.text
