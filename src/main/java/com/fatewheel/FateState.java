package com.fatewheel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;

/** Состояние одного игрока. Хранится в persistent data игрока под ключом "fatewheel". */
public final class FateState {
    private static final int VERSION = 2;

    /** Битовая маска всего проигранного (Stake.bit). Навсегда, пока не отыграно. */
    public int lostMask;

    // --- Настоящее: текущее задание ---
    public int taskId = -1;
    public int taskGoal;
    public int taskProgress;
    public int cycleTicks;
    public boolean taskDone;
    public String taskWord = "";
    public double startY;
    public double walked;

    // --- Прошлое + Настоящее: где игрок ставил блоки (измерение -> позиции, по порядку постановки) ---
    public static final int MAX_PLACED = 8192;
    public final Map<String, LinkedHashSet<Long>> placed = new HashMap<>();
    public int placedCount;
    public int eraseTicks;

    // --- не сохраняется ---
    public boolean wasCrouching;
    public double lastX, lastY, lastZ;
    public boolean hasLast;
    public int stillTicks;
    public int sentLost = -1;
    public boolean needSync = true;
    public int warnTicks;

    public boolean past() {
        return lost(Stake.PAST);
    }

    public boolean present() {
        return lost(Stake.PRESENT);
    }

    public boolean future() {
        return lost(Stake.FUTURE);
    }

    public boolean anyCurse() {
        return (lostMask & Stake.TIME_MASK) != 0;
    }

    public boolean lost(Stake s) {
        return (lostMask & s.bit) != 0;
    }

    public int lostLegs() {
        return (lost(Stake.LEFT_LEG) ? 1 : 0) + (lost(Stake.RIGHT_LEG) ? 1 : 0);
    }

    public int lostFeet() {
        return (lost(Stake.LEFT_FOOT) ? 1 : 0) + (lost(Stake.RIGHT_FOOT) ? 1 : 0);
    }

    public boolean noArms() {
        return lost(Stake.LEFT_ARM) && lost(Stake.RIGHT_ARM);
    }

    public void clearTask() {
        taskId = -1;
        taskGoal = 0;
        taskProgress = 0;
        cycleTicks = 0;
        taskDone = false;
        taskWord = "";
        startY = 0;
        walked = 0;
    }

    public void addPlaced(String dim, long pos) {
        if (placed.computeIfAbsent(dim, d -> new LinkedHashSet<>()).add(pos)) placedCount++;
        // слишком много — забываем самые старые
        while (placedCount > MAX_PLACED) {
            Iterator<LinkedHashSet<Long>> sets = placed.values().iterator();
            LinkedHashSet<Long> biggest = sets.next();
            while (sets.hasNext()) {
                LinkedHashSet<Long> next = sets.next();
                if (next.size() > biggest.size()) biggest = next;
            }
            Iterator<Long> it = biggest.iterator();
            it.next();
            it.remove();
            placedCount--;
        }
    }

    public void removePlaced(String dim, long pos) {
        LinkedHashSet<Long> set = placed.get(dim);
        if (set != null && set.remove(pos)) placedCount--;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("v", VERSION);
        t.putInt("lost", lostMask);
        t.putInt("task", taskId);
        t.putInt("goal", taskGoal);
        t.putInt("progress", taskProgress);
        t.putInt("cycle", cycleTicks);
        t.putBoolean("done", taskDone);
        t.putString("word", taskWord);
        t.putDouble("startY", startY);
        t.putDouble("walked", walked);
        t.putInt("erase", eraseTicks);
        CompoundTag pl = new CompoundTag();
        placed.forEach((dim, set) -> {
            if (set.isEmpty()) return;
            long[] arr = new long[set.size()];
            int i = 0;
            for (long v : set) arr[i++] = v;
            pl.put(dim, new LongArrayTag(arr));
        });
        t.put("placed", pl);
        return t;
    }

    public static FateState load(CompoundTag t) {
        FateState s = new FateState();
        int lost = t.getIntOr("lost", 0);
        if (t.getIntOr("v", 1) < 2) {
            // версия 1: 0 прошлое, 1 настоящее, 2 будущее, 3 л.рука, 4 п.рука, 5 ноги, 6 глаза, 7 язык, 8 сердце;
            // ещё раньше проклятия времени хранились таймерами
            int m = lost & 0b11111; // время и руки на тех же местах
            if (t.getIntOr("past", 0) != 0) m |= Stake.PAST.bit;
            if (t.getIntOr("present", 0) != 0) m |= Stake.PRESENT.bit;
            if (t.getIntOr("future", 0) != 0) m |= Stake.FUTURE.bit;
            if ((lost & (1 << 5)) != 0) m |= Stake.LEFT_LEG.bit | Stake.RIGHT_LEG.bit;
            if ((lost & (1 << 6)) != 0) m |= Stake.LEFT_EYE.bit | Stake.RIGHT_EYE.bit;
            if ((lost & (1 << 7)) != 0) m |= Stake.TONGUE.bit;
            if ((lost & (1 << 8)) != 0) m |= Stake.HEART.bit;
            lost = m;
        }
        s.lostMask = lost;
        s.taskId = t.getIntOr("task", -1);
        s.taskGoal = t.getIntOr("goal", 0);
        s.taskProgress = t.getIntOr("progress", 0);
        s.cycleTicks = t.getIntOr("cycle", 0);
        s.taskDone = t.getBooleanOr("done", false);
        s.taskWord = t.getStringOr("word", "");
        s.startY = t.getDoubleOr("startY", 0);
        s.walked = t.getDoubleOr("walked", 0);
        s.eraseTicks = t.getIntOr("erase", 0);
        CompoundTag pl = t.getCompoundOrEmpty("placed");
        for (String dim : pl.keySet()) {
            for (long v : pl.getLongArray(dim).orElse(new long[0])) s.addPlaced(dim, v);
        }
        // задание без сохранённой стартовой высоты выдаём заново, чтобы «Поднимись» не засчитывалось само
        if (!t.contains("startY")) s.clearTask();
        return s;
    }
}
