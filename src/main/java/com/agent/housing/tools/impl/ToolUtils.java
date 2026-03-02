package com.agent.housing.tools.impl;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

final class ToolUtils {

    private ToolUtils() {
    }

    /** 从 k1,v1,k2,v2... 构建 query 参数 Map，null 或空串不放入 */
    static Map<String, String> queryParams(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = kv[i];
            String val = kv[i + 1];
            if (val != null && !val.isEmpty()) {
                m.put(key, val);
            }
        }
        return m;
    }

    static String pathEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
