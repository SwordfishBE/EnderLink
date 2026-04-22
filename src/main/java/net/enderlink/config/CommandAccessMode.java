package net.enderlink.config;

import java.util.Locale;

public enum CommandAccessMode {
    EVERYONE,
    OPS_ONLY;

    public static CommandAccessMode normalize(CommandAccessMode value, CommandAccessMode fallback) {
        return value == null ? fallback : value;
    }

    public static CommandAccessMode parseOrDefault(String value, CommandAccessMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return CommandAccessMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}

