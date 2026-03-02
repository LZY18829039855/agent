package com.agent.housing.tools.impl;

import com.agent.housing.client.HousingApiClient;

import java.util.Collections;
import java.util.Map;

/**
 * 地标相关 5 个 Tool 实现（无需 X-User-ID）。
 */
public class LandmarkTools {

    private final HousingApiClient client;

    public LandmarkTools(HousingApiClient client) {
        this.client = client;
    }

    /** 1. 获取地标列表：category、district 可选 */
    public String getLandmarks(String category, String district) throws Exception {
        Map<String, String> params = ToolUtils.queryParams(
                "category", category,
                "district", district);
        return client.get("/api/landmarks", params, false);
    }

    /** 2. 按名称精确查询地标 */
    public String getLandmarkByName(String name) throws Exception {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name 必填");
        }
        String path = "/api/landmarks/name/" + ToolUtils.pathEncode(name);
        return client.get(path, Collections.<String, String>emptyMap(), false);
    }

    /** 3. 关键词模糊搜索地标，q 必填 */
    public String searchLandmarks(String q, String category, String district) throws Exception {
        if (q == null || q.isEmpty()) {
            throw new IllegalArgumentException("q 必填");
        }
        Map<String, String> params = ToolUtils.queryParams(
                "q", q,
                "category", category,
                "district", district);
        return client.get("/api/landmarks/search", params, false);
    }

    /** 4. 按地标 id 查询详情 */
    public String getLandmarkById(String id) throws Exception {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id 必填");
        }
        String path = "/api/landmarks/" + ToolUtils.pathEncode(id);
        return client.get(path, Collections.<String, String>emptyMap(), false);
    }

    /** 5. 获取地标统计信息 */
    public String getLandmarkStats() throws Exception {
        return client.get("/api/landmarks/stats", Collections.<String, String>emptyMap(), false);
    }
}
