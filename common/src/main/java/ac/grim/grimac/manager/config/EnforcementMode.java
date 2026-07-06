package ac.grim.grimac.manager.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum EnforcementMode {
    ENFORCE,
    MONITOR,
    OFF;

    public boolean enforces() {
        return this == ENFORCE;
    }

    public boolean recordsFlags() {
        return this != OFF;
    }

    public static @NotNull EnforcementMode fromConfig(@Nullable String value, @NotNull EnforcementMode fallback) {
        if (value == null || value.isBlank()) return fallback;

        return switch (value.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "enforce", "enforced", "strict", "on", "true" -> ENFORCE;
            case "monitor", "monitor-only", "alert", "alerts" -> MONITOR;
            case "off", "disabled", "disable", "false" -> OFF;
            default -> fallback;
        };
    }
}
