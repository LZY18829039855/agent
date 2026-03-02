"""
16 个工具的 OpenAI 兼容 schema，与 Java ToolDefinitions 一致。
返回格式：{ "type": "function", "function": { "name", "description", "parameters" } }
"""


def _prop(name: str, type_: str, description: str, required: bool = False) -> dict:
    p = {"name": name, "type": type_, "description": description}
    if required:
        p["required"] = True
    return p


def _properties(*props: dict) -> dict:
    properties = {}
    required = []
    for p in props:
        name = p["name"]
        properties[name] = {"type": p["type"], "description": p["description"]}
        if p.get("required"):
            required.append(name)
    out = {"type": "object", "properties": properties}
    if required:
        out["required"] = required
    return out


def _function_tool(name: str, description: str, parameters: dict) -> dict:
    return {"name": name, "description": description, "parameters": parameters}


def get_openai_format_tools() -> list[dict]:
    """返回 OpenAI 格式的 tools 列表，可直接传给大模型 API 的 tools 参数。"""
    schemas = [
        _function_tool(
            "get_landmarks",
            "获取地标列表，支持 category、district 同时筛选（取交集）。用于查地铁站、公司、商圈等地标。不需 X-User-ID。",
            _properties(
                _prop("category", "string", "地标类别：subway(地铁)/company(公司)/landmark(商圈等)，不传则不过滤"),
                _prop("district", "string", "行政区，如 海淀、朝阳"),
            ),
        ),
        _function_tool(
            "get_landmark_by_name",
            "按名称精确查询地标，如西二旗站、百度。返回地标 id、经纬度等，用于后续 nearby 查房。不需 X-User-ID。",
            _properties(_prop("name", "string", "地标名称，如 西二旗站、国贸", required=True)),
        ),
        _function_tool(
            "search_landmarks",
            "关键词模糊搜索地标，q 必填。支持 category、district 同时筛选，多条件取交集。不需 X-User-ID。",
            _properties(
                _prop("q", "string", "搜索关键词，必填", required=True),
                _prop("category", "string", "可选，限定类别：subway/company/landmark"),
                _prop("district", "string", "可选，限定行政区，如 海淀、朝阳"),
            ),
        ),
        _function_tool(
            "get_landmark_by_id",
            "按地标 id 查询地标详情。不需 X-User-ID。",
            _properties(_prop("id", "string", "地标 ID，如 SS_001、LM_002", required=True)),
        ),
        _function_tool(
            "get_landmark_stats",
            "获取地标统计信息（总数、按类别分布等）。不需 X-User-ID。",
            _properties(),
        ),
        _function_tool(
            "houses_init",
            "房源数据重置。建议每新起一个 session 就调用一次，保障每次用例执行都能使用初始化的数据。调用时请求头必带 X-User-ID。",
            _properties(),
        ),
        _function_tool(
            "get_house_by_id",
            "根据房源 ID 获取单套房源详情。无 query 参数，仅路径带 house_id，返回一条（安居客）。调用时请求头必带 X-User-ID。",
            _properties(_prop("house_id", "string", "房源 ID，如 HF_2001", required=True)),
        ),
        _function_tool(
            "get_house_listings",
            "根据房源 ID 获取该房源在链家/安居客/58同城等各平台的全部挂牌记录。调用时请求头必带 X-User-ID。",
            _properties(_prop("house_id", "string", "房源 ID，如 HF_2001", required=True)),
        ),
        _function_tool(
            "get_houses_by_community",
            "按小区名查询该小区下可租房源。默认每页 10 条、未传 listing_platform 时只返回安居客。调用时请求头必带 X-User-ID。",
            _properties(
                _prop("community", "string", "小区名，与数据一致", required=True),
                _prop("listing_platform", "string", "挂牌平台：链家/安居客/58同城，不传则默认安居客"),
                _prop("page", "integer", "页码，默认 1"),
                _prop("page_size", "integer", "每页条数，默认 10，最大 10000"),
            ),
        ),
        _function_tool(
            "get_houses_by_platform",
            "查询可租房源，支持按挂牌平台筛选。支持行政区、商圈、价格、户型、地铁距离等筛选。调用时请求头必带 X-User-ID。",
            _properties(
                _prop("listing_platform", "string", "挂牌平台：链家/安居客/58同城，不传则默认安居客"),
                _prop("district", "string", "行政区，逗号分隔，如 海淀,朝阳"),
                _prop("area", "string", "商圈，逗号分隔，如 西二旗,上地"),
                _prop("min_price", "integer", "最低月租金（元）"),
                _prop("max_price", "integer", "最高月租金（元）"),
                _prop("bedrooms", "string", "卧室数，逗号分隔，如 1,2"),
                _prop("rental_type", "string", "整租 或 合租"),
                _prop("decoration", "string", "精装/简装 等"),
                _prop("orientation", "string", "朝向，如 朝南、南北"),
                _prop("elevator", "string", "是否有电梯：true/false"),
                _prop("min_area", "integer", "最小面积（平米）"),
                _prop("max_area", "integer", "最大面积（平米）"),
                _prop("property_type", "string", "物业类型，如 住宅"),
                _prop("subway_line", "string", "地铁线路，如 13号线"),
                _prop("max_subway_dist", "integer", "最大地铁距离（米），近地铁建议 800"),
                _prop("subway_station", "string", "地铁站名，如 车公庄站"),
                _prop("utilities_type", "string", "水电类型，如 民水民电"),
                _prop("available_from_before", "string", "可入住日期上限，YYYY-MM-DD"),
                _prop("commute_to_xierqi_max", "integer", "到西二旗通勤时间上限（分钟）"),
                _prop("sort_by", "string", "排序字段：price/area/subway"),
                _prop("sort_order", "string", "asc 或 desc"),
                _prop("page", "integer", "页码，默认 1"),
                _prop("page_size", "integer", "每页条数，默认 10，最大 10000"),
            ),
        ),
        _function_tool(
            "get_houses_nearby",
            "以地标为圆心，查询在指定距离内的可租房源。需先通过地标接口获得 landmark_id。调用时请求头必带 X-User-ID。",
            _properties(
                _prop("landmark_id", "string", "地标 ID 或地标名称", required=True),
                _prop("max_distance", "number", "最大直线距离（米），默认 2000"),
                _prop("listing_platform", "string", "挂牌平台：链家/安居客/58同城，不传则默认安居客"),
                _prop("page", "integer", "页码，默认 1"),
                _prop("page_size", "integer", "每页条数，默认 10，最大 10000"),
            ),
        ),
        _function_tool(
            "get_nearby_landmarks",
            "查询某小区周边某类地标（商超/公园），按距离排序。调用时请求头必带 X-User-ID。",
            _properties(
                _prop("community", "string", "小区名，用于定位基准点", required=True),
                _prop("type", "string", "地标类型：shopping(商超) 或 park(公园)，不传则不过滤"),
                _prop("max_distance_m", "number", "最大距离（米），默认 3000"),
            ),
        ),
        _function_tool(
            "get_house_stats",
            "获取房源统计信息（总套数、按状态/行政区/户型分布等），按当前用户视角统计。调用时请求头必带 X-User-ID。",
            _properties(),
        ),
        _function_tool(
            "rent_house",
            "将当前用户视角下该房源设为已租。传入房源 ID 与 listing_platform（必填，链家/安居客/58同城）。必须调用此 API 才算完成租房操作。调用时请求头必带 X-User-ID。",
            _properties(
                _prop("house_id", "string", "房源 ID，如 HF_2001", required=True),
                _prop("listing_platform", "string", "必填。链家/安居客/58同城 之一", required=True),
            ),
        ),
        _function_tool(
            "terminate_rental",
            "将当前用户视角下该房源恢复为可租（退租）。传入房源 ID 与 listing_platform（必填）。调用时请求头必带 X-User-ID。",
            _properties(
                _prop("house_id", "string", "房源 ID，如 HF_2001", required=True),
                _prop("listing_platform", "string", "必填。链家/安居客/58同城 之一", required=True),
            ),
        ),
        _function_tool(
            "take_offline",
            "将当前用户视角下该房源设为下架。传入房源 ID 与 listing_platform（必填）。调用时请求头必带 X-User-ID。",
            _properties(
                _prop("house_id", "string", "房源 ID，如 HF_2001", required=True),
                _prop("listing_platform", "string", "必填。链家/安居客/58同城 之一", required=True),
            ),
        ),
    ]
    return [{"type": "function", "function": fn} for fn in schemas]


def parse_arguments(arguments_json: str | None) -> dict:
    """从 LLM 返回的 arguments JSON 字符串解析为 dict，供 ToolExecutor 使用。"""
    if not arguments_json or not arguments_json.strip():
        return {}
    import json
    return json.loads(arguments_json.strip())
