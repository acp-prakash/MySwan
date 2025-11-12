package org.myswan.common;

import com.fasterxml.jackson.databind.JsonNode;

public class UtilHelper {

    public static String getFirstNonNull(JsonNode node, String... keys) {
        for (String k : keys) {
            if (node.hasNonNull(k)) {
                String val = node.get(k).asText();
                if (!val.isBlank()) return val;
            }
        }
        return null;
    }
}
