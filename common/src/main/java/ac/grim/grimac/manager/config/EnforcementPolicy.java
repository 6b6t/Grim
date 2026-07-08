package ac.grim.grimac.manager.config;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class EnforcementPolicy {
    private static final Set<String> SAFETY_CHECK_NAMES = Set.of(
            "BadPacketsD",
            "BadPacketsI",
            "BadPacketsK",
            "BadPacketsL",
            "BadPacketsN",
            "BadPacketsO",
            "BadPacketsP",
            "BadPacketsQ",
            "BadPacketsS",
            "BadPacketsU",
            "BadPacketsW",
            "BadPacketsY",
            "InvalidBreak",
            "InvalidPlaceA",
            "InvalidPlaceB"
    );

    private static final Set<String> SAFETY_STABLE_KEYS = Set.of(
            "grim.badpackets.invalid_pitch",
            "grim.badpackets.spoofed_abilities",
            "grim.badpackets.invalid_spectate",
            "grim.badpackets.invalid_dig",
            "grim.badpackets.invalid_keepalive",
            "grim.badpackets.invalid_click",
            "grim.badpackets.invalid_horse_jump",
            "grim.badpackets.window_confirmation_not_accepted",
            "grim.badpackets.invalid_block_placement",
            "grim.badpackets.oob_slot",
            "grim.badpackets.invalid_teleport",
            "grim.badpackets.invalid_entity_target",
            "grim.breaking.invalid_break",
            "grim.scaffolding.invalid_place_a",
            "grim.scaffolding.invalid_place_b"
    );

    private final CompatibilityProfile profile;
    private final @Nullable ConfigManager config;
    private final EnforcementRule safetyRule;
    private final EnforcementRule gameplayRule;
    private final Map<CheckCategory, EnforcementRule> categoryRules;

    private EnforcementPolicy(
            @NotNull CompatibilityProfile profile,
            @Nullable ConfigManager config,
            @NotNull EnforcementRule safetyRule,
            @NotNull EnforcementRule gameplayRule,
            @NotNull Map<CheckCategory, EnforcementRule> categoryRules
    ) {
        this.profile = profile;
        this.config = config;
        this.safetyRule = safetyRule;
        this.gameplayRule = gameplayRule;
        this.categoryRules = categoryRules;
    }

    public static @NotNull EnforcementPolicy strict() {
        return new EnforcementPolicy(
                CompatibilityProfile.STRICT,
                null,
                EnforcementRule.enforce(),
                EnforcementRule.enforce(),
                new EnumMap<>(CheckCategory.class)
        );
    }

    public static @NotNull EnforcementPolicy strict(@NotNull ConfigManager config) {
        return load(config, CompatibilityProfile.STRICT);
    }

    public static @NotNull EnforcementPolicy load(@NotNull ConfigManager config) {
        return load(config, CompatibilityProfile.fromConfig(config.getStringElse("compatibility.profile", "strict")));
    }

    private static @NotNull EnforcementPolicy load(@NotNull ConfigManager config, @NotNull CompatibilityProfile profile) {
        EnforcementRule baseSafety = EnforcementRule.enforce();
        EnforcementRule baseGameplay = switch (profile) {
            case STRICT -> EnforcementRule.enforce();
            case NCP_LOOSE -> EnforcementRule.enforce();
            case ANARCHY -> EnforcementRule.off();
        };

        EnforcementRule safetyRule = readRule(config, "compatibility.safety", baseSafety);
        EnforcementRule gameplayRule = readRule(config, "compatibility.gameplay", baseGameplay);
        gameplayRule = readRule(config, "compatibility", gameplayRule);

        Map<CheckCategory, EnforcementRule> categoryRules = new EnumMap<>(CheckCategory.class);
        for (CheckCategory category : CheckCategory.values()) {
            if (category == CheckCategory.AUTO) continue;
            EnforcementRule fallback = category.isSafety() ? safetyRule : gameplayRule;
            categoryRules.put(category, readRule(config, "compatibility.categories." + category.configKey(), fallback));
        }

        return new EnforcementPolicy(profile, config, safetyRule, gameplayRule, categoryRules);
    }

    public @NotNull CompatibilityProfile profile() {
        return profile;
    }

    public @NotNull CheckCategory categoryFor(
            @Nullable String checkName,
            @Nullable String configName,
            @Nullable String stableKey,
            @Nullable CheckCategory configuredCategory
    ) {
        CheckCategory categoryOverride = CheckCategory.fromConfig(firstString(
                "compatibility.checks." + configKey(configName) + ".category",
                "compatibility.checks." + configKey(checkName) + ".category"
        ));
        if (categoryOverride != null && categoryOverride != CheckCategory.AUTO) return categoryOverride;
        if (configuredCategory != null && configuredCategory != CheckCategory.AUTO) return configuredCategory;

        String normalizedStableKey = stableKey == null ? "" : stableKey.toLowerCase(Locale.ROOT);
        String normalizedCheckName = checkName == null ? "" : checkName;
        if (isSafety(normalizedCheckName, normalizedStableKey)) return CheckCategory.SAFETY;

        if (normalizedStableKey.startsWith("grim.prediction.")
                || normalizedStableKey.startsWith("grim.groundspoof.")
                || normalizedStableKey.startsWith("grim.movement.")
                || normalizedStableKey.startsWith("grim.sprint.")) {
            return CheckCategory.MOVEMENT;
        }
        if (normalizedStableKey.startsWith("grim.timer.")) return CheckCategory.TIMER;
        if (normalizedStableKey.startsWith("grim.velocity.")) return CheckCategory.VELOCITY;
        if (normalizedStableKey.startsWith("grim.elytra.")) return CheckCategory.ELYTRA;
        if (normalizedStableKey.startsWith("grim.vehicle.")) return CheckCategory.VEHICLE;
        if (normalizedStableKey.startsWith("grim.combat.")
                || normalizedStableKey.startsWith("grim.multiinteract.")
                || normalizedStableKey.startsWith("grim.aim.")) {
            return CheckCategory.COMBAT;
        }
        if (normalizedStableKey.startsWith("grim.scaffolding.")
                || normalizedStableKey.startsWith("grim.breaking.")) {
            return CheckCategory.BLOCK;
        }
        if (normalizedStableKey.startsWith("grim.badpackets.")) return CheckCategory.BAD_PACKETS;
        if (normalizedStableKey.startsWith("grim.packetorder.")
                || normalizedStableKey.startsWith("grim.ping.")
                || normalizedStableKey.startsWith("grim.post.")) {
            return CheckCategory.PACKET_ORDER;
        }
        if (normalizedStableKey.startsWith("grim.multiactions.")) return CheckCategory.MULTI_ACTION;
        if (normalizedStableKey.startsWith("grim.chat.")) return CheckCategory.CHAT;

        return fallbackCategory(normalizedCheckName);
    }

    public @NotNull CheckCategory categoryFor(@NotNull Check check) {
        CheckData data = check.getClass().getAnnotation(CheckData.class);
        CheckCategory configuredCategory = data == null ? CheckCategory.AUTO : data.category();
        return categoryFor(check.getCheckName(), check.getConfigName(), check.getStableKey(), configuredCategory);
    }

    public @NotNull EnforcementRule ruleFor(
            @Nullable String checkName,
            @Nullable String configName,
            @Nullable String stableKey,
            @Nullable CheckCategory configuredCategory
    ) {
        CheckCategory category = categoryFor(checkName, configName, stableKey, configuredCategory);
        EnforcementRule categoryRule = categoryRules.getOrDefault(category, category.isSafety() ? safetyRule : gameplayRule);
        if (config == null) return categoryRule;

        EnforcementRule configuredRule = readRule(config, "compatibility.checks." + configKey(configName), categoryRule);
        return readRule(config, "compatibility.checks." + configKey(checkName), configuredRule);
    }

    public @NotNull EnforcementRule ruleFor(@NotNull Check check) {
        CheckData data = check.getClass().getAnnotation(CheckData.class);
        CheckCategory configuredCategory = data == null ? CheckCategory.AUTO : data.category();
        return ruleFor(check.getCheckName(), check.getConfigName(), check.getStableKey(), configuredCategory);
    }

    public @NotNull EnforcementMode modeFor(@NotNull Check check) {
        return ruleFor(check).mode();
    }

    public boolean isSafety(@NotNull Check check) {
        return categoryFor(check).isSafety();
    }

    public boolean shouldRecordFlags(@NotNull Check check) {
        return modeFor(check).recordsFlags();
    }

    public boolean isMonitorOnly(@NotNull Check check) {
        return modeFor(check) == EnforcementMode.MONITOR;
    }

    public boolean shouldModifyPackets(@NotNull Check check) {
        EnforcementRule rule = ruleFor(check);
        return rule.mode() == EnforcementMode.ENFORCE && rule.modifyPackets();
    }

    public boolean shouldSetback(@NotNull Check check) {
        EnforcementRule rule = ruleFor(check);
        return rule.mode() == EnforcementMode.ENFORCE && rule.setbacks();
    }

    public boolean isGameplayModifyPackets() {
        return gameplayRule.mode() == EnforcementMode.ENFORCE && gameplayRule.modifyPackets();
    }

    public boolean isGameplaySetbacks() {
        return gameplayRule.mode() == EnforcementMode.ENFORCE && gameplayRule.setbacks();
    }

    public boolean shouldEnforce(
            @Nullable String checkName,
            @Nullable String configName,
            @Nullable String stableKey,
            @Nullable CheckCategory configuredCategory
    ) {
        return ruleFor(checkName, configName, stableKey, configuredCategory).mode() == EnforcementMode.ENFORCE;
    }

    public boolean isMonitorOnly(
            @Nullable String checkName,
            @Nullable String configName,
            @Nullable String stableKey,
            @Nullable CheckCategory configuredCategory
    ) {
        return ruleFor(checkName, configName, stableKey, configuredCategory).mode() == EnforcementMode.MONITOR;
    }

    public boolean isOff(
            @Nullable String checkName,
            @Nullable String configName,
            @Nullable String stableKey,
            @Nullable CheckCategory configuredCategory
    ) {
        return ruleFor(checkName, configName, stableKey, configuredCategory).mode() == EnforcementMode.OFF;
    }

    public boolean shouldModifyPackets(
            @Nullable String checkName,
            @Nullable String configName,
            @Nullable String stableKey,
            @Nullable CheckCategory configuredCategory
    ) {
        EnforcementRule rule = ruleFor(checkName, configName, stableKey, configuredCategory);
        return rule.mode() == EnforcementMode.ENFORCE && rule.modifyPackets();
    }

    public boolean shouldSetback(
            @Nullable String checkName,
            @Nullable String configName,
            @Nullable String stableKey,
            @Nullable CheckCategory configuredCategory
    ) {
        EnforcementRule rule = ruleFor(checkName, configName, stableKey, configuredCategory);
        return rule.mode() == EnforcementMode.ENFORCE && rule.setbacks();
    }

    private static boolean isSafety(@NotNull String checkName, @NotNull String stableKey) {
        return checkName.startsWith("Crash")
                || checkName.startsWith("Exploit")
                || stableKey.startsWith("grim.crash.")
                || stableKey.startsWith("grim.exploit.")
                || SAFETY_CHECK_NAMES.contains(checkName)
                || SAFETY_STABLE_KEYS.contains(stableKey);
    }

    private static @NotNull CheckCategory fallbackCategory(@NotNull String checkName) {
        if (checkName.startsWith("Aim")
                || checkName.startsWith("Reach")
                || checkName.startsWith("Hitboxes")
                || checkName.startsWith("MultiInteract")
                || checkName.startsWith("SelfInteract")) {
            return CheckCategory.COMBAT;
        }
        if (checkName.startsWith("Timer")
                || checkName.startsWith("TickTimer")
                || checkName.startsWith("NegativeTimer")
                || checkName.startsWith("VehicleTimer")) {
            return CheckCategory.TIMER;
        }
        if (checkName.startsWith("Vehicle")) return CheckCategory.VEHICLE;
        if (checkName.startsWith("Elytra")) return CheckCategory.ELYTRA;
        if (checkName.startsWith("BadPackets")) return CheckCategory.BAD_PACKETS;
        if (checkName.startsWith("PacketOrder") || checkName.equals("TransactionOrder") || checkName.equals("Post")) {
            return CheckCategory.PACKET_ORDER;
        }
        if (checkName.startsWith("MultiActions")) return CheckCategory.MULTI_ACTION;
        if (checkName.startsWith("Chat")) return CheckCategory.CHAT;
        return CheckCategory.MISC;
    }

    private static @NotNull EnforcementRule readRule(
            @NotNull ConfigManager config,
            @NotNull String path,
            @NotNull EnforcementRule fallback
    ) {
        EnforcementMode mode = EnforcementMode.fromConfig(config.getStringElse(path + ".mode", null), fallback.mode());
        boolean modifyPackets = config.getBooleanElse(path + ".modify-packets", fallback.modifyPackets());
        boolean setbacks = config.getBooleanElse(path + ".setbacks", fallback.setbacks());
        return new EnforcementRule(mode, modifyPackets, setbacks);
    }

    private @Nullable String firstString(@NotNull String firstPath, @NotNull String secondPath) {
        if (config == null) return null;
        String value = config.getStringElse(firstPath, null);
        if (value != null && !value.isBlank()) return value;
        return config.getStringElse(secondPath, null);
    }

    private static @NotNull String configKey(@Nullable String value) {
        if (value == null || value.isBlank()) return "";
        return value.trim();
    }

    public record EnforcementRule(@NotNull EnforcementMode mode, boolean modifyPackets, boolean setbacks) {
        public static @NotNull EnforcementRule enforce() {
            return new EnforcementRule(EnforcementMode.ENFORCE, true, true);
        }

        public static @NotNull EnforcementRule monitor() {
            return new EnforcementRule(EnforcementMode.MONITOR, false, false);
        }

        public static @NotNull EnforcementRule off() {
            return new EnforcementRule(EnforcementMode.OFF, false, false);
        }
    }
}
