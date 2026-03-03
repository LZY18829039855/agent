package com.agent.housing.tools;

import com.agent.housing.client.HousingApiClient;
import com.agent.housing.config.AgentConfig;
import com.agent.housing.tools.impl.HouseTools;
import com.agent.housing.tools.impl.LandmarkTools;

import java.util.Collections;
import java.util.Map;

/**
 * 统一 Tool 执行器：根据工具名和参数调用对应 API，返回 JSON 字符串结果。
 */
public class ToolExecutor {

    private final LandmarkTools landmarkTools;
    private final HouseTools houseTools;

    public ToolExecutor(AgentConfig config) {
        HousingApiClient client = new HousingApiClient(config);
        this.landmarkTools = new LandmarkTools(client);
        this.houseTools = new HouseTools(client);
    }

    /**
     * 执行指定工具。
     *
     * @param toolName   工具名，与 ToolDefinitions 中的 name 一致
     * @param arguments  参数 Map，可从 ToolDefinitions.parseArguments(modelOutput) 得到
     * @return API 返回的 JSON 字符串
     */
    public String execute(String toolName, Map<String, Object> arguments) throws Exception {
        if (toolName == null || toolName.isEmpty()) {
            throw new IllegalArgumentException("toolName 不能为空");
        }
        Map<String, Object> args = arguments != null ? arguments : Collections.<String, Object>emptyMap();

        switch (toolName) {
            case "get_landmarks":
                return landmarkTools.getLandmarks(str(args, "category"), str(args, "district"));
            case "get_landmark_by_name":
                return landmarkTools.getLandmarkByName(str(args, "name"));
            case "search_landmarks":
                return landmarkTools.searchLandmarks(str(args, "q"), str(args, "category"), str(args, "district"));
            case "get_landmark_by_id":
                return landmarkTools.getLandmarkById(str(args, "id"));
            case "get_landmark_stats":
                return landmarkTools.getLandmarkStats();

            case "houses_init":
                return houseTools.housesInit();
            case "get_house_by_id":
                return houseTools.getHouseById(str(args, "house_id"));
            case "get_house_listings":
                return houseTools.getHouseListings(str(args, "house_id"));
            case "get_houses_by_community":
                return houseTools.getHousesByCommunity(
                        str(args, "community"),
                        str(args, "listing_platform"),
                        intOrNull(args, "page"),
                        intOrNull(args, "page_size"));
            case "get_houses_by_platform":
                return houseTools.getHousesByPlatform(
                        str(args, "listing_platform"),
                        str(args, "district"),
                        str(args, "area"),
                        intOrNull(args, "min_price"),
                        intOrNull(args, "max_price"),
                        str(args, "bedrooms"),
                        str(args, "rental_type"),
                        str(args, "decoration"),
                        str(args, "orientation"),
                        str(args, "elevator"),
                        intOrNull(args, "min_area"),
                        intOrNull(args, "max_area"),
                        str(args, "property_type"),
                        str(args, "subway_line"),
                        intOrNull(args, "max_subway_dist"),
                        str(args, "subway_station"),
                        str(args, "utilities_type"),
                        str(args, "available_from_before"),
                        intOrNull(args, "commute_to_xierqi_max"),
                        str(args, "sort_by"),
                        str(args, "sort_order"),
                        intOrNull(args, "page"),
                        intOrNull(args, "page_size"));
            case "get_houses_by_platform_and_tags":
                return houseTools.getHousesByPlatformAndTags(
                        str(args, "listing_platform"),
                        str(args, "district"),
                        str(args, "tags"),
                        str(args, "hidden_noise_level"),
                        str(args, "area"),
                        intOrNull(args, "min_price"),
                        intOrNull(args, "max_price"),
                        str(args, "bedrooms"),
                        str(args, "rental_type"),
                        str(args, "decoration"),
                        str(args, "orientation"),
                        str(args, "elevator"),
                        intOrNull(args, "min_area"),
                        intOrNull(args, "max_area"),
                        str(args, "property_type"),
                        str(args, "subway_line"),
                        intOrNull(args, "max_subway_dist"),
                        str(args, "subway_station"),
                        str(args, "utilities_type"),
                        str(args, "available_from_before"),
                        intOrNull(args, "commute_to_xierqi_max"),
                        str(args, "sort_by"),
                        str(args, "sort_order"),
                        intOrNull(args, "page"),
                        intOrNull(args, "page_size"));
            case "get_houses_nearby":
                return houseTools.getHousesNearby(
                        str(args, "landmark_id"),
                        doubleOrNull(args, "max_distance"),
                        str(args, "listing_platform"),
                        intOrNull(args, "page"),
                        intOrNull(args, "page_size"));
            case "get_nearby_landmarks":
                return houseTools.getNearbyLandmarks(
                        str(args, "community"),
                        str(args, "type"),
                        doubleOrNull(args, "max_distance_m"));
            case "get_house_stats":
                return houseTools.getHouseStats();
            case "rent_house":
                return houseTools.rentHouse(str(args, "house_id"), str(args, "listing_platform"));
            case "terminate_rental":
                return houseTools.terminateRental(str(args, "house_id"), str(args, "listing_platform"));
            case "take_offline":
                return houseTools.takeOffline(str(args, "house_id"), str(args, "listing_platform"));
            default:
                throw new IllegalArgumentException("未知工具: " + toolName);
        }
    }

    /**
     * 执行工具，参数为 LLM 返回的 arguments JSON 字符串。
     */
    public String execute(String toolName, String argumentsJson) throws Exception {
        Map<String, Object> args = ToolDefinitions.parseArguments(argumentsJson);
        return execute(toolName, args);
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof String) {
            return (String) v;
        }
        return String.valueOf(v);
    }

    private static Integer intOrNull(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return Integer.parseInt((String) v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Double doubleOrNull(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v instanceof String) {
            try {
                return Double.parseDouble((String) v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
