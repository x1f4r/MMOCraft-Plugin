package com.x1f4r.mmocraft.util;

import com.x1f4r.mmocraft.playerdata.model.Stat;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A very basic utility for "JSON-like" serialization and deserialization of simple maps.
 * This is a placeholder and should ideally be replaced by a proper JSON library like Gson or Jackson.
 */
public class JsonUtil {

    // Pattern to match "STAT_NAME":value entries
    private static final Pattern STAT_ENTRY_PATTERN = Pattern.compile("\"([A-Z_]+)\":([0-9]*\\.?[0-9]+)");

    /**
     * Converts a Map<Stat, Double> to a simple JSON-like string.
     * Example: {"STRENGTH":10.0,"AGILITY":12.5}
     *
     * @param statsMap The map of stats to serialize.
     * @return A JSON-like string representation.
     */
    public static String statsMapToJson(Map<Stat, Double> statsMap) {
        if (statsMap == null || statsMap.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<Stat, Double> entry : statsMap.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(entry.getKey().name()).append("\":").append(entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Converts a JSON-like string back to a Map<Stat, Double>.
     * This parser is very basic and expects a specific format: {"STAT_NAME":value,...}
     * It does not handle complex JSON structures, escaping, or various data types robustly.
     *
     * @param json The JSON-like string to deserialize.
     * @return A Map<Stat, Double> populated from the string. Returns an empty map if JSON is null, empty, or malformed.
     */
    public static Map<Stat, Double> jsonToStatsMap(String json) {
        Map<Stat, Double> statsMap = new EnumMap<>(Stat.class);
        if (json == null || json.isEmpty() || !json.startsWith("{") || !json.endsWith("}")) {
            return statsMap; // Return empty map for invalid/empty JSON
        }

        Matcher matcher = STAT_ENTRY_PATTERN.matcher(json);
        while (matcher.find()) {
            try {
                String statName = matcher.group(1);
                String statValueStr = matcher.group(2);
                Stat stat = Stat.valueOf(statName); // Can throw IllegalArgumentException if statName is invalid
                double value = Double.parseDouble(statValueStr); // Can throw NumberFormatException
                statsMap.put(stat, value);
            } catch (IllegalArgumentException e) { // Removed redundant NumberFormatException
                // Log this error or handle it more gracefully in a real application
                // For now, we'll skip malformed entries.
                System.err.println("Error parsing stat entry in JSON: " + matcher.group(0) + " - " + e.getMessage());
            }
        }
        return statsMap;
    }
}
