package ac.grim.grimac.manager.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum CompatibilityProfile {
    STRICT,
    NCP_LOOSE,
    ANARCHY;

    public static @NotNull CompatibilityProfile fromConfig(@Nullable String value) {
        if (value == null || value.isBlank()) return STRICT;

        return switch (value.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "ncp-loose", "loose-ncp", "nocheatplus-loose" -> NCP_LOOSE;
            case "anarchy" -> ANARCHY;
            default -> STRICT;
        };
    }
}
