package ac.grim.grimac.manager.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum CheckCategory {
    AUTO,
    SAFETY,
    MOVEMENT,
    TIMER,
    VELOCITY,
    ELYTRA,
    VEHICLE,
    COMBAT,
    BLOCK,
    BAD_PACKETS,
    PACKET_ORDER,
    MULTI_ACTION,
    INVENTORY,
    CHAT,
    MISC;

    public boolean isSafety() {
        return this == SAFETY;
    }

    public @NotNull String configKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static @Nullable CheckCategory fromConfig(@Nullable String value) {
        if (value == null || value.isBlank()) return null;

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return CheckCategory.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
