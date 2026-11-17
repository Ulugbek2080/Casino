package com.fatewheel;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.List;

/**
 * Всё, что можно поставить в Апгрейдере вместо предмета: время и органы.
 * Проигранное теряется навсегда, но его можно отыграть обратно.
 * Порядок — это номер бита в маске, его нельзя менять (он хранится в сохранениях).
 */
public enum Stake {
    PAST(true, "Прошлое", ChatFormatting.AQUA, 3000,
            "Не видно ни названий, ни иконок вещей."),
    PRESENT(true, "Настоящее", ChatFormatting.GOLD, 5000,
            "Каждую минуту задание.", "Не успели — смерть."),
    FUTURE(true, "Будущее", ChatFormatting.LIGHT_PURPLE, 4000,
            "Нельзя подбирать предметы с земли."),
    LEFT_ARM(false, "Левая рука", ChatFormatting.RED, 120,
            "Во второй руке ничего не удержать."),
    RIGHT_ARM(false, "Правая рука", ChatFormatting.RED, 200,
            "Основной рукой ничего не сделать:", "работает только левая (F — взять в неё)."),
    LEFT_LEG(false, "Левая нога", ChatFormatting.RED, 120,
            "Скорость −40%, прыжок слабее.", "Без обеих ног — ни шагу."),
    RIGHT_LEG(false, "Правая нога", ChatFormatting.RED, 120,
            "Скорость −40%, прыжок слабее.", "Без обеих ног — ни шагу."),
    LEFT_EYE(false, "Левый глаз", ChatFormatting.RED, 80,
            "Левая четверть экрана темнеет.", "Без обоих глаз — слепота."),
    RIGHT_EYE(false, "Правый глаз", ChatFormatting.RED, 80,
            "Правая четверть экрана темнеет.", "Без обоих глаз — слепота."),
    TONGUE(false, "Язык", ChatFormatting.RED, 40,
            "Вместо слов в чате — мычание."),
    HEART(false, "Половина сердца", ChatFormatting.RED, 300,
            "Максимум здоровья −5 сердец."),
    // --- добавлены позже: только в конец, номера битов не трогать ---
    LEFT_EAR(false, "Левое ухо", ChatFormatting.RED, 40,
            "Не слышно звуков слева.", "Без обоих ушей — полная глухота."),
    RIGHT_EAR(false, "Правое ухо", ChatFormatting.RED, 40,
            "Не слышно звуков справа.", "Без обоих ушей — полная глухота."),
    LEFT_HAND(false, "Левая кисть", ChatFormatting.RED, 60,
            "Во второй руке можно держать,", "но не пользоваться (щит, еда, факелы)."),
    RIGHT_HAND(false, "Правая кисть", ChatFormatting.RED, 90,
            "Копание и атака вдвое медленнее."),
    LEFT_FOOT(false, "Левая ступня", ChatFormatting.RED, 50,
            "Скорость −15%, падения больнее.", "Без обеих ступней — не побегать."),
    RIGHT_FOOT(false, "Правая ступня", ChatFormatting.RED, 50,
            "Скорость −15%, падения больнее.", "Без обеих ступней — не побегать.");

    public static final int TIME_MASK = PAST.bit | PRESENT.bit | FUTURE.bit;
    public static final int BODY_MASK = ((1 << values().length) - 1) & ~TIME_MASK;

    public final boolean time;
    public final String title;
    public final ChatFormatting color;
    public final int defaultValue;
    public final List<String> description;
    public final int bit = 1 << ordinal();

    Stake(boolean time, String title, ChatFormatting color, int defaultValue, String... description) {
        this.time = time;
        this.title = title;
        this.color = color;
        this.defaultValue = defaultValue;
        this.description = List.of(description);
    }

    /** Кисть держится на руке, ступня — на ноге: без руки кисть уже не поставить. */
    public Stake parent() {
        return switch (this) {
            case LEFT_HAND -> LEFT_ARM;
            case RIGHT_HAND -> RIGHT_ARM;
            case LEFT_FOOT -> LEFT_LEG;
            case RIGHT_FOOT -> RIGHT_LEG;
            default -> null;
        };
    }

    /** Что нельзя поставить при таких потерях: уже потерянное и то, что держалось на потерянном. */
    public static int unstakeable(int lostMask) {
        return lostMask | childrenOf(lostMask);
    }

    /** Части, которые держатся на чём-то из mask (кисти на руках, ступни на ногах). */
    public static int childrenOf(int mask) {
        int m = 0;
        for (Stake s : values()) {
            Stake parent = s.parent();
            if (parent != null && (mask & parent.bit) != 0) m |= s.bit;
        }
        return m;
    }

    public ItemLike icon() {
        return switch (this) {
            case PAST -> Items.CLOCK;
            case PRESENT -> Items.COMPASS;
            case FUTURE -> Items.ENDER_EYE;
            default -> Items.BARRIER;
        };
    }

    /** Ценность ставки в единицах Upgrader (алмаз ≈ 400). */
    public int value() {
        return FateConfig.stakeValue(this);
    }

    /** Ценность «вернуть это». */
    public int restoreValue() {
        return (int) Math.max(1, Math.round(value() * FateConfig.RESTORE_MULTIPLIER.get()));
    }

    public static long sumValue(int mask) {
        long sum = 0;
        for (Stake s : values()) if ((mask & s.bit) != 0) sum += s.value();
        return sum;
    }

    public static long sumRestore(int mask) {
        long sum = 0;
        for (Stake s : values()) if ((mask & s.bit) != 0) sum += s.restoreValue();
        return sum;
    }

    public static List<String> titles(int mask) {
        List<String> out = new java.util.ArrayList<>();
        for (Stake s : values()) if ((mask & s.bit) != 0) out.add(s.title);
        return out;
    }
}
