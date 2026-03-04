package com.agent.housing.tools.impl;

import com.agent.housing.client.HousingApiClient;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 房源相关 10 个 Tool + 数据重置 init（所有请求带 X-User-ID）。
 */
public class HouseTools {

    private static final Gson GSON = new Gson();
    private final HousingApiClient client;

    public HouseTools(HousingApiClient client) {
        this.client = client;
    }

    /** 数据重置：建议每个新 session 调用一次 */
    public String housesInit() throws Exception {
        return client.post("/api/houses/init", Collections.<String, String>emptyMap(), true);
    }

    /** 6. 根据房源 ID 获取单套房源详情 */
    public String getHouseById(String houseId) throws Exception {
        if (houseId == null || houseId.isEmpty()) {
            throw new IllegalArgumentException("house_id 必填");
        }
        String path = "/api/houses/" + ToolUtils.pathEncode(houseId);
        return client.get(path, Collections.<String, String>emptyMap(), true);
    }

    /** 7. 根据房源 ID 获取各平台挂牌记录 */
    public String getHouseListings(String houseId) throws Exception {
        if (houseId == null || houseId.isEmpty()) {
            throw new IllegalArgumentException("house_id 必填");
        }
        String path = "/api/houses/listings/" + ToolUtils.pathEncode(houseId);
        return client.get(path, Collections.<String, String>emptyMap(), true);
    }

    /** 8. 按小区名查询可租房源 */
    public String getHousesByCommunity(String community, String listingPlatform, Integer page, Integer pageSize) throws Exception {
        if (community == null || community.isEmpty()) {
            throw new IllegalArgumentException("community 必填");
        }
        Map<String, String> params = ToolUtils.queryParams(
                "community", community,
                "listing_platform", listingPlatform,
                "page", page != null ? String.valueOf(page) : null,
                "page_size", pageSize != null ? String.valueOf(pageSize) : null);
        return client.get("/api/houses/by_community", params, true);
    }

    /** 9. 按挂牌平台/多条件查询可租房源 */
    public String getHousesByPlatform(
            String listingPlatform, String district, String area,
            Integer minPrice, Integer maxPrice, String bedrooms, String rentalType,
            String decoration, String orientation, String elevator,
            Integer minArea, Integer maxArea, String propertyType,
            String subwayLine, Integer maxSubwayDist, String subwayStation,
            String utilitiesType, String availableFromBefore, Integer commuteToXierqiMax,
            String sortBy, String sortOrder, Integer page, Integer pageSize) throws Exception {
        Map<String, String> params = ToolUtils.queryParams(
                "listing_platform", listingPlatform,
                "district", district,
                "area", area,
                "min_price", minPrice != null ? String.valueOf(minPrice) : null,
                "max_price", maxPrice != null ? String.valueOf(maxPrice) : null,
                "bedrooms", bedrooms,
                "rental_type", rentalType,
                "decoration", decoration,
                "orientation", orientation,
                "elevator", elevator,
                "min_area", minArea != null ? String.valueOf(minArea) : null,
                "max_area", maxArea != null ? String.valueOf(maxArea) : null,
                "property_type", propertyType,
                "subway_line", subwayLine,
                "max_subway_dist", maxSubwayDist != null ? String.valueOf(maxSubwayDist) : null,
                "subway_station", subwayStation,
                "utilities_type", utilitiesType,
                "available_from_before", availableFromBefore,
                "commute_to_xierqi_max", commuteToXierqiMax != null ? String.valueOf(commuteToXierqiMax) : null,
                "sort_by", sortBy,
                "sort_order", sortOrder,
                "page", page != null ? String.valueOf(page) : null,
                "page_size", pageSize != null ? String.valueOf(pageSize) : null);
        return client.get("/api/houses/by_platform", params, true);
    }

    /**
     * 按挂牌平台及房屋标签（tags）、噪声环境（hidden_noise_level）筛选房源。
     * 先调用 /api/houses/by_platform 获取数据，再在本地过滤 tags 与 hidden_noise_level 后返回。
     */
    public String getHousesByPlatformAndTags(
            String listingPlatform, String district, String tags, String hiddenNoiseLevel, String area,
            Integer minPrice, Integer maxPrice, String bedrooms, String rentalType,
            String decoration, String orientation, String elevator,
            Integer minArea, Integer maxArea, String propertyType,
            String subwayLine, Integer maxSubwayDist, String subwayStation,
            String utilitiesType, String availableFromBefore, Integer commuteToXierqiMax,
            String sortBy, String sortOrder, Integer page, Integer pageSize) throws Exception {
        // 向远端只传 by_platform 支持的参数，不传 tags / hidden_noise_level；拉取较大一页以便本地过滤后再分页
        String raw = getHousesByPlatform(
                listingPlatform, district, area,
                minPrice, maxPrice, bedrooms, rentalType,
                decoration, orientation, elevator,
                minArea, maxArea, propertyType,
                subwayLine, maxSubwayDist, subwayStation,
                utilitiesType, availableFromBefore, commuteToXierqiMax,
                sortBy, sortOrder, 1, 10000);
        JsonObject root = GSON.fromJson(raw, JsonObject.class);
        if (root == null || !root.has("data")) {
            return raw;
        }
        JsonObject data = root.getAsJsonObject("data");
        if (!data.has("items")) {
            return raw;
        }
        JsonArray items = data.getAsJsonArray("items");
        List<String> requiredTags = parseTagList(tags);
        List<JsonElement> filtered = new ArrayList<>();
        for (JsonElement el : items) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject house = el.getAsJsonObject();
            if (!matchTags(house, requiredTags)) {
                continue;
            }
            if (!matchHiddenNoiseLevel(house, hiddenNoiseLevel)) {
                continue;
            }
            filtered.add(el);
        }
        int total = filtered.size();
        int pageNum = (page != null && page > 0) ? page : 1;
        int size = (pageSize != null && pageSize > 0) ? pageSize : 100;
        int from = (pageNum - 1) * size;
        int to = Math.min(from + size, total);
        JsonArray resultItems = new JsonArray();
        for (int i = from; i < to && i < filtered.size(); i++) {
            resultItems.add(filtered.get(i));
        }
        JsonObject newData = new JsonObject();
        newData.addProperty("total", total);
        newData.addProperty("page", pageNum);
        newData.addProperty("page_size", size);
        newData.add("items", resultItems);
        JsonObject out = new JsonObject();
        out.addProperty("code", root.has("code") ? root.get("code").getAsInt() : 0);
        out.addProperty("message", root.has("message") ? root.get("message").getAsString() : "success");
        out.add("data", newData);
        return GSON.toJson(out);
    }

    /** 解析 tags 参数字符串为列表（支持逗号、顿号分隔），去空、去首尾空格 */
    private static List<String> parseTagList(String tags) {
        List<String> list = new ArrayList<>();
        if (tags == null || tags.isEmpty()) {
            return list;
        }
        for (String s : tags.split("[,、]")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                list.add(t);
            }
        }
        return list;
    }

    /** 若未要求 tags 则通过；否则要求房屋 tags 包含所有要求的标签 */
    private static boolean matchTags(JsonObject house, List<String> requiredTags) {
        if (requiredTags.isEmpty()) {
            return true;
        }
        if (!house.has("tags") || !house.get("tags").isJsonArray()) {
            return false;
        }
        JsonArray arr = house.getAsJsonArray("tags");
        List<String> houseTags = new ArrayList<>();
        for (JsonElement e : arr) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                houseTags.add(e.getAsString().trim());
            }
        }
        for (String required : requiredTags) {
            if (!houseTags.contains(required)) {
                return false;
            }
        }
        return true;
    }

    /** 若未要求 hidden_noise_level 则通过；否则要求房屋 hidden_noise_level 与之相等 */
    private static boolean matchHiddenNoiseLevel(JsonObject house, String hiddenNoiseLevel) {
        if (hiddenNoiseLevel == null || hiddenNoiseLevel.isEmpty()) {
            return true;
        }
        if (!house.has("hidden_noise_level") || house.get("hidden_noise_level").isJsonNull()) {
            return false;
        }
        return hiddenNoiseLevel.trim().equals(house.get("hidden_noise_level").getAsString().trim());
    }

    /** 10. 以地标为圆心查附近房源 */
    public String getHousesNearby(String landmarkId, Double maxDistance, String listingPlatform, Integer page, Integer pageSize) throws Exception {
        if (landmarkId == null || landmarkId.isEmpty()) {
            throw new IllegalArgumentException("landmark_id 必填");
        }
        Map<String, String> params = ToolUtils.queryParams(
                "landmark_id", landmarkId,
                "max_distance", maxDistance != null ? String.valueOf(maxDistance) : null,
                "listing_platform", listingPlatform,
                "page", page != null ? String.valueOf(page) : null,
                "page_size", pageSize != null ? String.valueOf(pageSize) : null);
        return client.get("/api/houses/nearby", params, true);
    }

    /** 11. 查询某小区周边地标（商超/公园） */
    public String getNearbyLandmarks(String community, String type, Double maxDistanceM) throws Exception {
        if (community == null || community.isEmpty()) {
            throw new IllegalArgumentException("community 必填");
        }
        Map<String, String> params = ToolUtils.queryParams(
                "community", community,
                "type", type,
                "max_distance_m", maxDistanceM != null ? String.valueOf(maxDistanceM) : null);
        return client.get("/api/houses/nearby_landmarks", params, true);
    }

    /** 12. 获取房源统计信息 */
    public String getHouseStats() throws Exception {
        return client.get("/api/houses/stats", Collections.<String, String>emptyMap(), true);
    }

    /** 13. 租房：listing_platform 必填，链家/安居客/58同城 */
    public String rentHouse(String houseId, String listingPlatform) throws Exception {
        if (houseId == null || houseId.isEmpty()) {
            throw new IllegalArgumentException("house_id 必填");
        }
        if (listingPlatform == null || listingPlatform.isEmpty()) {
            throw new IllegalArgumentException("listing_platform 必填");
        }
        Map<String, String> params = new HashMap<String, String>();
        params.put("listing_platform", listingPlatform);
        String path = "/api/houses/" + ToolUtils.pathEncode(houseId) + "/rent";
        return client.post(path, params, true);
    }

    /** 14. 退租 */
    public String terminateRental(String houseId, String listingPlatform) throws Exception {
        if (houseId == null || houseId.isEmpty()) {
            throw new IllegalArgumentException("house_id 必填");
        }
        if (listingPlatform == null || listingPlatform.isEmpty()) {
            throw new IllegalArgumentException("listing_platform 必填");
        }
        Map<String, String> params = new HashMap<String, String>();
        params.put("listing_platform", listingPlatform);
        String path = "/api/houses/" + ToolUtils.pathEncode(houseId) + "/terminate";
        return client.post(path, params, true);
    }

    /** 15. 下架 */
    public String takeOffline(String houseId, String listingPlatform) throws Exception {
        if (houseId == null || houseId.isEmpty()) {
            throw new IllegalArgumentException("house_id 必填");
        }
        if (listingPlatform == null || listingPlatform.isEmpty()) {
            throw new IllegalArgumentException("listing_platform 必填");
        }
        Map<String, String> params = new HashMap<String, String>();
        params.put("listing_platform", listingPlatform);
        String path = "/api/houses/" + ToolUtils.pathEncode(houseId) + "/offline";
        return client.post(path, params, true);
    }
}
