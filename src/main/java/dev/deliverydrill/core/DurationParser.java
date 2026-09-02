package dev.deliverydrill.core;

import java.time.Duration;
import java.util.Locale;

public final class DurationParser {
    private DurationParser() { }

    public static Duration parse(String value) {
        if (value == null || value.isBlank()) return Duration.ZERO;
        String v = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (v.matches("[-+]?\\d+")) return Duration.ofMillis(Long.parseLong(v));
            if (v.endsWith("ms")) return Duration.ofMillis(Long.parseLong(v.substring(0, v.length() - 2).trim()));
            if (v.endsWith("s")) return Duration.ofMillis((long) (Double.parseDouble(v.substring(0, v.length() - 1).trim()) * 1000));
            if (v.endsWith("m")) return Duration.ofMillis((long) (Double.parseDouble(v.substring(0, v.length() - 1).trim()) * 60_000));
            return Duration.parse(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid duration '" + value + "' (use e.g. 500ms, 5s, or 1m)", e);
        }
    }
}

