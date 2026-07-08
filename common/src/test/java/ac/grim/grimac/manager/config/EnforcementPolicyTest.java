package ac.grim.grimac.manager.config;

import ac.grim.grimac.api.config.ConfigManager;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnforcementPolicyTest {

    @Test
    void strictProfileEnforcesGameplayByDefault() {
        EnforcementPolicy policy = policy();

        assertEquals(CheckCategory.COMBAT, policy.categoryFor("Reach", "Reach", "grim.combat.reach", CheckCategory.AUTO));
        assertEquals(EnforcementMode.ENFORCE, policy.ruleFor("Reach", "Reach", "grim.combat.reach", CheckCategory.AUTO).mode());
        assertTrue(policy.shouldModifyPackets("Reach", "Reach", "grim.combat.reach", CheckCategory.AUTO));
        assertTrue(policy.shouldSetback("Reach", "Reach", "grim.combat.reach", CheckCategory.AUTO));
    }

    @Test
    void ncpLooseProfileEnforcesGameplayAndSafetyByDefault() {
        EnforcementPolicy policy = policy("compatibility.profile", "ncp-loose");

        assertEquals(EnforcementMode.ENFORCE, policy.ruleFor("Reach", "Reach", "grim.combat.reach", CheckCategory.AUTO).mode());
        assertTrue(policy.shouldModifyPackets("Reach", "Reach", "grim.combat.reach", CheckCategory.AUTO));
        assertTrue(policy.shouldSetback("Reach", "Reach", "grim.combat.reach", CheckCategory.AUTO));

        assertEquals(CheckCategory.SAFETY, policy.categoryFor("BadPacketsD", "BadPacketsD", "grim.badpackets.invalid_pitch", CheckCategory.AUTO));
        assertTrue(policy.shouldModifyPackets("BadPacketsD", "BadPacketsD", "grim.badpackets.invalid_pitch", CheckCategory.AUTO));
        assertTrue(policy.shouldSetback("BadPacketsD", "BadPacketsD", "grim.badpackets.invalid_pitch", CheckCategory.AUTO));
    }

    @Test
    void ncpLooseProfileKeepsGameplayBadPacketsOutOfSafety() {
        EnforcementPolicy policy = policy("compatibility.profile", "ncp-loose");

        assertEquals(CheckCategory.BAD_PACKETS, policy.categoryFor("BadPacketsF", "BadPacketsF", "grim.badpackets.duplicate_sprint", CheckCategory.AUTO));
        assertEquals(EnforcementMode.ENFORCE, policy.ruleFor("BadPacketsF", "BadPacketsF", "grim.badpackets.duplicate_sprint", CheckCategory.AUTO).mode());
        assertTrue(policy.shouldModifyPackets("BadPacketsF", "BadPacketsF", "grim.badpackets.duplicate_sprint", CheckCategory.AUTO));
    }

    @Test
    void categoryAndCheckOverridesCanLoosenOrDisableProfileDefaults() {
        EnforcementPolicy policy = policy(
                "compatibility.profile", "ncp-loose",
                "compatibility.categories.combat.mode", "monitor",
                "compatibility.categories.combat.modify-packets", false,
                "compatibility.categories.combat.setbacks", false,
                "compatibility.checks.Reach.mode", "off"
        );

        assertEquals(EnforcementMode.OFF, policy.ruleFor("Reach", "Reach", "grim.combat.reach", CheckCategory.AUTO).mode());
        assertFalse(policy.shouldModifyPackets("Reach", "Reach", "grim.combat.reach", CheckCategory.AUTO));

        assertEquals(EnforcementMode.MONITOR, policy.ruleFor("Hitboxes", "Hitboxes", "grim.combat.hitboxes", CheckCategory.AUTO).mode());
        assertFalse(policy.shouldModifyPackets("Hitboxes", "Hitboxes", "grim.combat.hitboxes", CheckCategory.AUTO));
    }

    private static EnforcementPolicy policy(Object... values) {
        return EnforcementPolicy.load(new MapConfig(Map.ofEntries(entries(values))));
    }

    private static Map.Entry<String, Object>[] entries(Object... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("values must be key/value pairs");
        }

        @SuppressWarnings("unchecked")
        Map.Entry<String, Object>[] entries = new Map.Entry[values.length / 2];
        for (int i = 0; i < values.length; i += 2) {
            entries[i / 2] = Map.entry((String) values[i], values[i + 1]);
        }
        return entries;
    }

    private record MapConfig(Map<String, Object> values) implements ConfigManager {
        @Override
        public String getStringElse(String key, String otherwise) {
            Object value = values.get(key);
            return value instanceof String string ? string : otherwise;
        }

        @Override
        public @Nullable String getString(String key) {
            Object value = values.get(key);
            return value instanceof String string ? string : null;
        }

        @Override
        public List<String> getStringList(String key) {
            return getStringListElse(key, List.of());
        }

        @Override
        public List<String> getStringListElse(String key, List<String> otherwise) {
            Object value = values.get(key);
            if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
                return list.stream().map(String.class::cast).toList();
            }
            return otherwise;
        }

        @Override
        public int getIntElse(String key, int other) {
            Object value = values.get(key);
            return value instanceof Number number ? number.intValue() : other;
        }

        @Override
        public long getLongElse(String key, long otherwise) {
            Object value = values.get(key);
            return value instanceof Number number ? number.longValue() : otherwise;
        }

        @Override
        public double getDoubleElse(String key, double otherwise) {
            Object value = values.get(key);
            return value instanceof Number number ? number.doubleValue() : otherwise;
        }

        @Override
        public boolean getBooleanElse(String key, boolean otherwise) {
            Object value = values.get(key);
            return value instanceof Boolean bool ? bool : otherwise;
        }

        @Override
        public <T> T get(String key) {
            @SuppressWarnings("unchecked")
            T value = (T) values.get(key);
            return value;
        }

        @Override
        public <T> @Nullable T getElse(String key, T otherwise) {
            T value = get(key);
            return value == null ? otherwise : value;
        }

        @Override
        public <K, V> Map<K, V> getMap(String key) {
            return getMapElse(key, Map.of());
        }

        @Override
        public <K, V> @Nullable Map<K, V> getMapElse(String key, Map<K, V> map) {
            Object value = values.get(key);
            if (value instanceof Map<?, ?> valueMap) {
                @SuppressWarnings("unchecked")
                Map<K, V> typed = (Map<K, V>) valueMap;
                return typed;
            }
            return map;
        }

        @Override
        public <T> @Nullable List<T> getList(String path) {
            return getListElse(path, null);
        }

        @Override
        public <T> @Nullable List<T> getListElse(String path, List<T> otherwise) {
            Object value = values.get(path);
            if (value instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<T> typed = (List<T>) list;
                return typed;
            }
            return otherwise;
        }

        @Override
        public boolean hasLoaded() {
            return true;
        }

        @Override
        public void reload() {
        }
    }
}
