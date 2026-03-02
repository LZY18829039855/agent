"""
统一工具执行器：根据工具名和参数调用对应仿真 API，与 Java ToolExecutor 一致。
"""
import urllib.parse

from housing_agent.config import get_housing_base_url, get_housing_user_id
from housing_agent.tools.client import HousingApiClient
from housing_agent.tools.schemas import parse_arguments


def _str(d: dict, key: str) -> str | None:
    v = d.get(key)
    if v is None:
        return None
    return str(v) if not isinstance(v, str) else v


def _int_or_none(d: dict, key: str) -> int | None:
    v = d.get(key)
    if v is None:
        return None
    if isinstance(v, int):
        return v
    if isinstance(v, (float, str)):
        try:
            return int(v) if not isinstance(v, str) else int(float(v)) if "." in str(v) else int(v)
        except (ValueError, TypeError):
            return None
    return None


def _double_or_none(d: dict, key: str) -> float | None:
    v = d.get(key)
    if v is None:
        return None
    if isinstance(v, (int, float)):
        return float(v)
    if isinstance(v, str):
        try:
            return float(v)
        except (ValueError, TypeError):
            return None
    return None


class ToolExecutor:
    def __init__(self, base_url: str | None = None, user_id: str | None = None):
        base_url = base_url or get_housing_base_url()
        user_id = user_id or get_housing_user_id()
        self._client = HousingApiClient(base_url, user_id)

    def execute(self, tool_name: str, arguments: dict | None = None) -> str:
        args = arguments or {}
        if not tool_name or not str(tool_name).strip():
            raise ValueError("toolName 不能为空")

        if tool_name == "get_landmarks":
            return self._client.get(
                "/api/landmarks",
                _query("category", _str(args, "category"), "district", _str(args, "district")),
                False,
            )
        if tool_name == "get_landmark_by_name":
            name = _str(args, "name")
            if not name:
                raise ValueError("name 必填")
            return self._client.get(f"/api/landmarks/name/{_path_encode(name)}", {}, False)
        if tool_name == "search_landmarks":
            q = _str(args, "q")
            if not q:
                raise ValueError("q 必填")
            return self._client.get(
                "/api/landmarks/search",
                _query("q", q, "category", _str(args, "category"), "district", _str(args, "district")),
                False,
            )
        if tool_name == "get_landmark_by_id":
            id_ = _str(args, "id")
            if not id_:
                raise ValueError("id 必填")
            return self._client.get(f"/api/landmarks/{_path_encode(id_)}", {}, False)
        if tool_name == "get_landmark_stats":
            return self._client.get("/api/landmarks/stats", {}, False)

        if tool_name == "houses_init":
            return self._client.post("/api/houses/init", {}, True)
        if tool_name == "get_house_by_id":
            house_id = _str(args, "house_id")
            if not house_id:
                raise ValueError("house_id 必填")
            return self._client.get(f"/api/houses/{_path_encode(house_id)}", {}, True)
        if tool_name == "get_house_listings":
            house_id = _str(args, "house_id")
            if not house_id:
                raise ValueError("house_id 必填")
            return self._client.get(f"/api/houses/listings/{_path_encode(house_id)}", {}, True)
        if tool_name == "get_houses_by_community":
            community = _str(args, "community")
            if not community:
                raise ValueError("community 必填")
            return self._client.get(
                "/api/houses/by_community",
                _query(
                    "community", community,
                    "listing_platform", _str(args, "listing_platform"),
                    "page", _int_or_none(args, "page"),
                    "page_size", _int_or_none(args, "page_size"),
                ),
                True,
            )
        if tool_name == "get_houses_by_platform":
            return self._client.get(
                "/api/houses/by_platform",
                _query(
                    "listing_platform", _str(args, "listing_platform"),
                    "district", _str(args, "district"),
                    "area", _str(args, "area"),
                    "min_price", _int_or_none(args, "min_price"),
                    "max_price", _int_or_none(args, "max_price"),
                    "bedrooms", _str(args, "bedrooms"),
                    "rental_type", _str(args, "rental_type"),
                    "decoration", _str(args, "decoration"),
                    "orientation", _str(args, "orientation"),
                    "elevator", _str(args, "elevator"),
                    "min_area", _int_or_none(args, "min_area"),
                    "max_area", _int_or_none(args, "max_area"),
                    "property_type", _str(args, "property_type"),
                    "subway_line", _str(args, "subway_line"),
                    "max_subway_dist", _int_or_none(args, "max_subway_dist"),
                    "subway_station", _str(args, "subway_station"),
                    "utilities_type", _str(args, "utilities_type"),
                    "available_from_before", _str(args, "available_from_before"),
                    "commute_to_xierqi_max", _int_or_none(args, "commute_to_xierqi_max"),
                    "sort_by", _str(args, "sort_by"),
                    "sort_order", _str(args, "sort_order"),
                    "page", _int_or_none(args, "page"),
                    "page_size", _int_or_none(args, "page_size"),
                ),
                True,
            )
        if tool_name == "get_houses_nearby":
            landmark_id = _str(args, "landmark_id")
            if not landmark_id:
                raise ValueError("landmark_id 必填")
            return self._client.get(
                "/api/houses/nearby",
                _query(
                    "landmark_id", landmark_id,
                    "max_distance", _double_or_none(args, "max_distance"),
                    "listing_platform", _str(args, "listing_platform"),
                    "page", _int_or_none(args, "page"),
                    "page_size", _int_or_none(args, "page_size"),
                ),
                True,
            )
        if tool_name == "get_nearby_landmarks":
            community = _str(args, "community")
            if not community:
                raise ValueError("community 必填")
            return self._client.get(
                "/api/houses/nearby_landmarks",
                _query(
                    "community", community,
                    "type", _str(args, "type"),
                    "max_distance_m", _double_or_none(args, "max_distance_m"),
                ),
                True,
            )
        if tool_name == "get_house_stats":
            return self._client.get("/api/houses/stats", {}, True)
        if tool_name == "rent_house":
            house_id = _str(args, "house_id")
            platform = _str(args, "listing_platform")
            if not house_id:
                raise ValueError("house_id 必填")
            if not platform:
                raise ValueError("listing_platform 必填")
            return self._client.post(
                f"/api/houses/{_path_encode(house_id)}/rent",
                {"listing_platform": platform},
                True,
            )
        if tool_name == "terminate_rental":
            house_id = _str(args, "house_id")
            platform = _str(args, "listing_platform")
            if not house_id:
                raise ValueError("house_id 必填")
            if not platform:
                raise ValueError("listing_platform 必填")
            return self._client.post(
                f"/api/houses/{_path_encode(house_id)}/terminate",
                {"listing_platform": platform},
                True,
            )
        if tool_name == "take_offline":
            house_id = _str(args, "house_id")
            platform = _str(args, "listing_platform")
            if not house_id:
                raise ValueError("house_id 必填")
            if not platform:
                raise ValueError("listing_platform 必填")
            return self._client.post(
                f"/api/houses/{_path_encode(house_id)}/offline",
                {"listing_platform": platform},
                True,
            )
        raise ValueError(f"未知工具: {tool_name}")

    def execute_json(self, tool_name: str, arguments_json: str) -> str:
        args = parse_arguments(arguments_json)
        return self.execute(tool_name, args)


def _query(*kv) -> dict:
    out = {}
    for i in range(0, len(kv), 2):
        if i + 1 < len(kv) and kv[i + 1] is not None and str(kv[i + 1]).strip() != "":
            out[kv[i]] = str(kv[i + 1])
    return out


def _path_encode(s: str) -> str:
    return urllib.parse.quote(s, safe="")
