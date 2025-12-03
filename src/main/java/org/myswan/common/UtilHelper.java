package org.myswan.common;

import com.fasterxml.jackson.databind.JsonNode;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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

    public static String checkForPresence(JSONObject json, String key) {
        if (json != null && json.has(key) && !json.isNull(key)) {
            return json.get(key).toString();
        }
        return "";
    }

    public static String stripStringToTwoDecimals(String value, boolean returnZero) {
        if (value == null || value.isEmpty()) {
            return returnZero ? "0.00" : "";
        }
        try {
            double num = Double.parseDouble(value);
            return String.format("%.2f", num);
        } catch (NumberFormatException e) {
            return returnZero ? "0.00" : "";
        }
    }

    public static String formatLocalDateToString(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
