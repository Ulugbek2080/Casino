package com.fatewheel;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

/**
 * Файл config/fatewheel-local.toml.
 * Шанс считается по правилам Upgrader (его house_edge, min_chance, max_chance).
 */
public final class FateConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue TASK_SECONDS;
    public static final ModConfigSpec.IntValue ERASE_SECONDS;
    public static final ModConfigSpec.DoubleValue RESTORE_MULTIPLIER;
    private static final Map<Stake, ModConfigSpec.IntValue> STAKE_VALUES = new EnumMap<>(Stake.class);
    private static final Map<String, ModConfigSpec.IntValue> SPECIAL_VALUES = new java.util.HashMap<>();
    private static final ModConfigSpec.IntValue UNIQUE_MIN_VALUE;
    /** Особые предметы и их ценность по умолчанию (id предмета без "fatewheel:"). */
    private static final String[][] SPECIAL_DEFAULTS = {
            {"chronometer", "3000"}, {"growth_hourglass", "25000"}, {"size_wand", "15000"},
            {"talisman_strength", "12000"}, {"talisman_speed", "8000"}, {"talisman_haste", "8000"},
            {"talisman_jump", "5000"}, {"talisman_night_vision", "5000"}, {"talisman_regeneration", "15000"},
            {"talisman_fire_resistance", "10000"}, {"talisman_resistance", "14000"},
    };

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Проигранное (время и органы) теряется навсегда. Вернуть можно только отыграв в Апгрейдере.")
                .push("present");
        TASK_SECONDS = b.comment("Настоящее: как часто выдаётся задание и сколько секунд на него даётся.")
                .defineInRange("task_seconds", 10, 10, 3600);
        ERASE_SECONDS = b.comment("Прошлое + Настоящее: как часто исчезают все блоки, поставленные игроком (секунды).",
                        "Сразу при получении комбо исчезает всё, что игрок строил раньше.")
                .defineInRange("erase_seconds", 60, 5, 3600);
        b.pop();

        b.comment("Ценность ставок в единицах Upgrader (для сравнения: алмаз ≈ 400, незеритовый лом ≈ 1200).")
                .push("stakes");
        for (Stake s : Stake.values()) {
            STAKE_VALUES.put(s, b.defineInRange(s.name().toLowerCase(), s.defaultValue, 1, 10_000_000));
        }
        RESTORE_MULTIPLIER = b.comment("Цель «вернуть X» стоит: ценность X * этот множитель.")
                .defineInRange("restore_multiplier", 1.5, 0.1, 100.0);
        b.pop();

        b.comment("Ценность особых предметов (категория «Особые» в Апгрейдере).").push("special");
        for (String[] d : SPECIAL_DEFAULTS) {
            SPECIAL_VALUES.put(d[0], b.defineInRange(d[0], Integer.parseInt(d[1]), 1, 10_000_000));
        }
        b.pop();

        b.comment("Уникальные предметы (элитры, тотем, звезда Незера, маяк...): цена не ниже этой.").push("unique");
        UNIQUE_MIN_VALUE = b.defineInRange("min_value", 10000, 0, 10_000_000);
        b.pop();

        SPEC = b.build();
    }

    private FateConfig() {
    }

    /** null — такого особого предмета нет; до загрузки конфига — значение по умолчанию. */
    public static Integer specialValue(String id) {
        ModConfigSpec.IntValue v = SPECIAL_VALUES.get(id);
        if (v == null) return null;
        try {
            return v.get();
        } catch (IllegalStateException notLoaded) {
            return v.getDefault();
        }
    }

    public static int uniqueMinValue() {
        try {
            return UNIQUE_MIN_VALUE.get();
        } catch (IllegalStateException notLoaded) {
            return UNIQUE_MIN_VALUE.getDefault();
        }
    }

    public static int stakeValue(Stake s) {
        return STAKE_VALUES.get(s).get();
    }
}
