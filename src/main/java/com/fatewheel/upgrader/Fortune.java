package com.fatewheel.upgrader;

import com.fatewheel.FateItems;
import com.fatewheel.FateState;
import com.fatewheel.Stake;
import net.execheinz.upgrader.value.ItemValues;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Колесо судьбы: на «Ничего» ставка сгорает, на любом другом секторе остаётся при игроке
 * и к ней добавляется приз ≈ множитель × ставка.
 * Средний возврат (RTP) = шанс выигрыша (ставка цела) + сумма шанс × множитель = 0.67 + 0.341 ≈ 101%:
 * мод в первую очередь весёлый — две трети вращений что-то приносят, а в среднем игрок остаётся при своих.
 * Шансы зашиты в код, а не в конфиг: клиент рисует те же секторы, что крутит сервер.
 */
public final class Fortune {
    public enum Tier {
        NOTHING("Ничего", 0.0, 33, 0xFF3A3F4A),
        SMALL("Мелочь", 0.2, 30, 0xFF4F7FB0),
        NICE("Неплохо", 0.35, 18, 0xFF3FA06A),
        LUCK("Удача", 0.6, 11, 0xFFC9A227),
        BIG("Крупно", 1.2, 6, 0xFFE0702A),
        JACKPOT("Джекпот", 4.0, 2, 0xFFD63AD6);

        public final String title;
        public final double multiplier;
        /** Шанс в процентах (сумма по всем — 100). */
        public final int percent;
        public final int color;

        Tier(String title, double multiplier, int percent, int color) {
            this.title = title;
            this.multiplier = multiplier;
            this.percent = percent;
            this.color = color;
        }
    }

    /** Порядок секторов по часовой стрелке от верха. Одинаковые уровни делят свой шанс поровну. */
    public static final Tier[] LAYOUT = {
            Tier.NOTHING, Tier.SMALL, Tier.LUCK, Tier.NICE, Tier.NOTHING, Tier.SMALL, Tier.BIG,
            Tier.NICE, Tier.NOTHING, Tier.SMALL, Tier.LUCK, Tier.JACKPOT};

    /** Начало и конец каждого сектора в градусах (0 — верх, по часовой). */
    public static final float[] FROM = new float[LAYOUT.length];
    public static final float[] TO = new float[LAYOUT.length];

    static {
        float a = 0;
        for (int i = 0; i < LAYOUT.length; i++) {
            Tier t = LAYOUT[i];
            int parts = 0;
            for (Tier o : LAYOUT) if (o == t) parts++;
            FROM[i] = a;
            a += t.percent * 3.6f / parts;
            TO[i] = a;
        }
        TO[LAYOUT.length - 1] = 360f;
    }

    /** Шанс выиграть хоть что-то. */
    public static final float WIN_CHANCE = (100 - Tier.NOTHING.percent) / 100f;

    private Fortune() {
    }

    public static Tier roll(RandomSource random) {
        int r = random.nextInt(100);
        for (Tier t : Tier.values()) {
            if (r < t.percent) return t;
            r -= t.percent;
        }
        return Tier.NOTHING;
    }

    /** Куда остановится стрелка: случайное место в одном из секторов этого уровня. */
    public static float landingAngle(Tier tier, RandomSource random) {
        List<Integer> sectors = new ArrayList<>();
        for (int i = 0; i < LAYOUT.length; i++) if (LAYOUT[i] == tier) sectors.add(i);
        int i = sectors.get(random.nextInt(sectors.size()));
        float span = TO[i] - FROM[i];
        float margin = Math.min(1.5f, span * 0.2f);
        return FROM[i] + margin + random.nextFloat() * Math.max(0.01f, span - margin * 2);
    }

    // ------------------------------------------------------------------ подбор приза

    /** Приз: предметы или возврат потерянного (restoreMask). */
    public record Prize(ItemStack stack, int restoreMask, double value) {
    }

    /**
     * Подобрать приз ценностью около target. Сначала ищем в пределах 0.6..1.5 от цели,
     * иначе — ближайший, но не дороже 2× цели. null — подходящего нет.
     */
    public static Prize pick(Level level, Tier tier, double target, FateState state, RandomSource random) {
        if (tier == Tier.NOTHING || target <= 0) return null;
        List<Prize> all = new ArrayList<>();
        if (tier == Tier.JACKPOT) {
            addItems(all, level, target, FateItems.UNIQUE_IDS);
            for (var special : FateItems.ALL) addItem(all, level, target, special.get());
        }
        // джекпот — в первую очередь уникальное или особое; если не по цене, то как остальные
        if (pick(all, target, random, 1.5) == null) {
            addItems(all, level, target, FateItems.FOOD_IDS);
            addItems(all, level, target, FateItems.DECOR_IDS);
            addItems(all, level, target, FateItems.RESOURCE_IDS);
            addItems(all, level, target, FateItems.UNIQUE_IDS);
            for (var special : FateItems.ALL) addItem(all, level, target, special.get());
            for (Stake s : Stake.values()) {
                if (state.lost(s)) all.add(new Prize(ItemStack.EMPTY, s.bit, s.restoreValue()));
            }
        }
        Prize p = pick(all, target, random, 1.5);
        if (p != null) return p;
        // ничего не попало в коридор — ближайшее, но без сильной переплаты
        Prize best = null;
        double bestDist = Double.MAX_VALUE;
        for (Prize c : all) {
            if (c.value > target * 2) continue;
            double d = Math.abs(Math.log(c.value / target));
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    private static Prize pick(List<Prize> all, double target, RandomSource random, double max) {
        List<Prize> ok = new ArrayList<>();
        for (Prize c : all) if (c.value >= target * 0.6 && c.value <= target * max) ok.add(c);
        return ok.isEmpty() ? null : ok.get(random.nextInt(ok.size()));
    }

    private static void addItems(List<Prize> out, Level level, double target, String[] ids) {
        for (String id : ids) {
            BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(id)).ifPresent(i -> addItem(out, level, target, i));
        }
    }

    /** Предмет в таком количестве, чтобы ценность была ближе к цели (не больше одного стака). */
    private static void addItem(List<Prize> out, Level level, double target, Item item) {
        if (ItemValues.isBlacklisted(item)) return;
        double unit = ItemValues.unitValue(level, item);
        if (unit <= 0) return;
        int max = Math.min(64, new ItemStack(item).getMaxStackSize());
        int count = (int) Math.max(1, Math.min(max, Math.round(target / unit)));
        out.add(new Prize(new ItemStack(item, count), 0, unit * count));
    }
}
