package com.agent.housing.tools;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 供 LLM Function Calling 使用的 Tool 定义（OpenAI 兼容格式）。
 * 返回的每个 tool 包含 type="function", function={ name, description, parameters }。
 */
public final class ToolDefinitions {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private ToolDefinitions() {
    }

    /**
     * 返回所有工具的 schema 列表，可直接传给大模型 API 的 tools 参数。
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getAllToolSchemas() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(functionTool("get_landmarks",
                "获取地标列表，支持 category、district 同时筛选（取交集）。用于查地铁站、公司、商圈等地标。不需 X-User-ID。",
                properties(
                        prop("category", "string", "地标类别：subway(地铁)/company(公司)/landmark(商圈等)，不传则不过滤"),
                        prop("district", "string", "行政区，如 海淀、朝阳"))));
        list.add(functionTool("get_landmark_by_name",
                "按名称精确查询地标，如西二旗站、百度。返回地标 id、经纬度等，用于后续 nearby 查房。不需 X-User-ID。",
                properties(propRequired("name", "string", "地标名称，如 西二旗站、国贸"))));
        list.add(functionTool("search_landmarks",
                "关键词模糊搜索地标，q 必填。支持 category、district 同时筛选，多条件取交集。不需 X-User-ID。",
                properties(
                        propRequired("q", "string", "搜索关键词，必填"),
                        prop("category", "string", "可选，限定类别：subway/company/landmark"),
                        prop("district", "string", "可选，限定行政区，如 海淀、朝阳"))));
        list.add(functionTool("get_landmark_by_id",
                "按地标 id 查询地标详情。不需 X-User-ID。",
                properties(propRequired("id", "string", "地标 ID，如 SS_001、LM_002"))));
        list.add(functionTool("get_landmark_stats",
                "获取地标统计信息（总数、按类别分布等）。不需 X-User-ID。",
                properties()));

        list.add(functionTool("houses_init",
                "房源数据重置。建议每新起一个 session 就调用一次，保障每次用例执行都能使用初始化的数据。调用时请求头必带 X-User-ID。",
                properties()));
        list.add(functionTool("get_house_by_id",
                "根据房源 ID 获取单套房源详情。无 query 参数，仅路径带 house_id，返回一条（安居客）。调用时请求头必带 X-User-ID。",
                properties(propRequired("house_id", "string", "房源 ID，如 HF_2001"))));
        list.add(functionTool("get_house_listings",
                "根据房源 ID 获取该房源在链家/安居客/58同城等各平台的全部挂牌记录。调用时请求头必带 X-User-ID。",
                properties(propRequired("house_id", "string", "房源 ID，如 HF_2001"))));
        list.add(functionTool("get_houses_by_community",
                "按小区名查询该小区下可租房源。默认每页 10 条、未传 listing_platform 时只返回安居客。用于指代消解、查某小区地铁信息或隐性属性。调用时请求头必带 X-User-ID。",
                properties(
                        propRequired("community", "string", "小区名，与数据一致，如 建清园(南区)、保利锦上(二期)"),
                        prop("listing_platform", "string", "挂牌平台：链家/安居客/58同城，不传则默认安居客"),
                        prop("page", "integer", "页码，默认 1"),
                        prop("page_size", "integer", "每页条数，默认 10，最大 10000"))));
        list.add(functionTool("get_houses_by_platform",
                "查询可租房源，支持按挂牌平台筛选。listing_platform 可选：不传则默认使用安居客；传 链家/安居客/58同城 则只返回该平台。支持行政区、商圈、价格、户型、地铁距离、西二旗通勤等筛选。调用时请求头必带 X-User-ID。",
                properties(
                        prop("listing_platform", "string", "挂牌平台：链家/安居客/58同城，不传则默认安居客"),
                        prop("district", "string", "行政区，逗号分隔，如 海淀,朝阳"),
                        prop("area", "string", "商圈，逗号分隔，如 西二旗,上地"),
                        prop("min_price", "integer", "最低月租金（元）"),
                        prop("max_price", "integer", "最高月租金（元）"),
                        prop("bedrooms", "string", "卧室数，逗号分隔，如 1,2"),
                        prop("rental_type", "string", "整租 或 合租"),
                        prop("decoration", "string", "精装/简装 等"),
                        prop("orientation", "string", "朝向，如 朝南、南北"),
                        prop("elevator", "string", "是否有电梯：true/false"),
                        prop("min_area", "integer", "最小面积（平米）"),
                        prop("max_area", "integer", "最大面积（平米）"),
                        prop("property_type", "string", "物业类型，如 住宅"),
                        prop("subway_line", "string", "地铁线路，如 13号线"),
                        prop("max_subway_dist", "integer", "最大地铁距离（米），近地铁建议 800"),
                        prop("subway_station", "string", "地铁站名，如 车公庄站"),
                        prop("utilities_type", "string", "水电类型，如 民水民电"),
                        prop("available_from_before", "string", "可入住日期上限，YYYY-MM-DD（如 2026-03-10）"),
                        prop("commute_to_xierqi_max", "integer", "到西二旗通勤时间上限（分钟）"),
                        prop("sort_by", "string", "排序字段：price/area/subway"),
                        prop("sort_order", "string", "asc 或 desc"),
                        prop("page", "integer", "页码，默认 1"),
                        prop("page_size", "integer", "每页条数，默认 10，最大 10000"))));
        list.add(functionTool("get_houses_by_platform_and_tags",
                "按挂牌平台以及房屋标签（是否包水电、安静等）筛选房源。listing_platform 可选；不传则默认安居客。先拉取 by_platform 数据再按 tags、hidden_noise_level 本地过滤后返回。调用时请求头必带 X-User-ID。",
                properties(
                        prop("listing_platform", "string", "挂牌平台：链家/安居客/58同城，不传则默认安居客"),
                        prop("district", "string", "行政区，逗号分隔，如 海淀,朝阳"),
                        prop("tags", "string", "房屋标签，如 包水电、房东直租（逗号或顿号分隔，房屋需同时具备）"),
                        prop("hidden_noise_level", "string", "噪声环境，如 安静"),
                        prop("area", "string", "商圈，逗号分隔，如 西二旗,上地"),
                        prop("min_price", "integer", "最低月租金（元）"),
                        prop("max_price", "integer", "最高月租金（元）"),
                        prop("bedrooms", "string", "卧室数，逗号分隔，如 1,2"),
                        prop("rental_type", "string", "整租 或 合租"),
                        prop("decoration", "string", "精装/简装 等"),
                        prop("orientation", "string", "朝向，如 朝南、南北"),
                        prop("elevator", "string", "是否有电梯：true/false"),
                        prop("min_area", "integer", "最小面积（平米）"),
                        prop("max_area", "integer", "最大面积（平米）"),
                        prop("property_type", "string", "物业类型，如 住宅"),
                        prop("subway_line", "string", "地铁线路，如 13号线"),
                        prop("max_subway_dist", "integer", "最大地铁距离（米），近地铁建议 800"),
                        prop("subway_station", "string", "地铁站名，如 车公庄站"),
                        prop("utilities_type", "string", "水电类型，如 民水民电"),
                        prop("available_from_before", "string", "可入住日期上限，YYYY-MM-DD（如 2026-03-10）"),
                        prop("commute_to_xierqi_max", "integer", "到西二旗通勤时间上限（分钟）"),
                        prop("sort_by", "string", "排序字段：price/area/subway"),
                        prop("sort_order", "string", "asc 或 desc"),
                        prop("page", "integer", "页码，默认 1"),
                        prop("page_size", "integer", "每页条数，默认 20，最大 10000"))));
        list.add(functionTool("get_houses_nearby",
                "以地标为圆心，查询在指定距离内的可租房源，返回带直线距离、步行距离、步行时间。需先通过地标接口获得 landmark_id。调用时请求头必带 X-User-ID。",
                properties(
                        propRequired("landmark_id", "string", "地标 ID 或地标名称（支持按名称查找）"),
                        prop("max_distance", "number", "最大直线距离（米），默认 2000"),
                        prop("listing_platform", "string", "挂牌平台：链家/安居客/58同城，不传则默认安居客"),
                        prop("page", "integer", "页码，默认 1"),
                        prop("page_size", "integer", "每页条数，默认 10，最大 10000"))));
        list.add(functionTool("get_nearby_landmarks",
                "查询某小区周边某类地标（商超/公园），按距离排序。用于回答「附近有没有商场/公园」。调用时请求头必带 X-User-ID。",
                properties(
                        propRequired("community", "string", "小区名，用于定位基准点"),
                        prop("type", "string", "地标类型：shopping(商超) 或 park(公园)，不传则不过滤"),
                        prop("max_distance_m", "number", "最大距离（米），默认 3000"))));
        list.add(functionTool("get_house_stats",
                "获取房源统计信息（总套数、按状态/行政区/户型分布、价格区间等），按当前用户视角统计。调用时请求头必带 X-User-ID。",
                properties()));
        list.add(functionTool("rent_house",
                "将当前用户视角下该房源设为已租。传入房源 ID 与 listing_platform（必填，链家/安居客/58同城）以明确租赁哪个平台；三平台状态一并更新。必须调用此 API 才算完成租房操作，仅对话生成已租无效。调用时请求头必带 X-User-ID。",
                properties(
                        propRequired("house_id", "string", "房源 ID，如 HF_2001"),
                        propRequired("listing_platform", "string", "必填。链家/安居客/58同城 之一"))));
        list.add(functionTool("terminate_rental",
                "将当前用户视角下该房源恢复为可租（退租）。传入房源 ID 与 listing_platform（必填）以明确操作哪个平台。调用时请求头必带 X-User-ID。",
                properties(
                        propRequired("house_id", "string", "房源 ID，如 HF_2001"),
                        propRequired("listing_platform", "string", "必填。链家/安居客/58同城 之一"))));
        list.add(functionTool("take_offline",
                "将当前用户视角下该房源设为下架。传入房源 ID 与 listing_platform（必填）以明确操作哪个平台。调用时请求头必带 X-User-ID。",
                properties(
                        propRequired("house_id", "string", "房源 ID，如 HF_2001"),
                        propRequired("listing_platform", "string", "必填。链家/安居客/58同城 之一"))));
        return list;
    }

    /**
     * 包装为 OpenAI 格式：{ "type": "function", "function": { "name", "description", "parameters" } }
     */
    public static List<Map<String, Object>> getOpenAIFormatTools() {
        List<Map<String, Object>> schemas = getAllToolSchemas();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> fn : schemas) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            tool.put("function", fn);
            result.add(tool);
        }
        return result;
    }

    private static Map<String, Object> functionTool(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("description", description);
        fn.put("parameters", parameters);
        return fn;
    }

    private static Map<String, Object> properties(Map<String, Object>... props) {
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Map<String, Object> p : props) {
            String key = (String) p.get("name");
            properties.put(key, p);
            if (Boolean.TRUE.equals(p.get("required"))) {
                required.add(key);
            }
        }
        params.put("type", "object");
        params.put("properties", properties);
        if (!required.isEmpty()) {
            params.put("required", required);
        }
        return params;
    }

    private static Map<String, Object> prop(String name, String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", name);
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    private static Map<String, Object> propRequired(String name, String type, String description) {
        Map<String, Object> p = prop(name, type, description);
        p.put("required", true);
        return p;
    }

    /** 从 JSON 字符串解析出 arguments Map，供 ToolExecutor 使用 */
    public static Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        return GSON.fromJson(argumentsJson.trim(), MAP_TYPE);
    }
}
