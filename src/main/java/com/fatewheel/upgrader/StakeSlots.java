package com.fatewheel.upgrader;

import net.execheinz.upgrader.menu.UpgraderMenu;
import net.execheinz.upgrader.value.ItemValues;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Ставка предметами — до 5 ячеек: родная ячейка Апгрейдера (над остальными) и 4 наши под ней.
 * Ценность ставки — сумма всех ячеек.
 */
public final class StakeSlots {
    public static final int EXTRA = 4;
    /** Позиции наших ячеек в окне (как у Slot: левый верхний угол предмета). Родная — 40,34. */
    public static final int[] X = {13, 31, 49, 67};
    public static final int Y = 54;

    private StakeSlots() {
    }

    public static List<ItemStack> stacks(UpgraderMenu menu) {
        List<ItemStack> out = new ArrayList<>(1 + EXTRA);
        out.add(menu.getInputStack());
        Container extra = ((FateMenuAccess) menu).fatewheel$extra();
        for (int i = 0; i < extra.getContainerSize(); i++) out.add(extra.getItem(i));
        return out;
    }

    public static boolean empty(UpgraderMenu menu) {
        for (ItemStack s : stacks(menu)) if (!s.isEmpty()) return false;
        return true;
    }

    public static boolean anyBlacklisted(UpgraderMenu menu) {
        for (ItemStack s : stacks(menu)) if (!s.isEmpty() && ItemValues.isBlacklisted(s.getItem())) return true;
        return false;
    }

    public static double value(Level level, UpgraderMenu menu) {
        double sum = 0;
        for (ItemStack s : stacks(menu)) if (!s.isEmpty()) sum += ItemValues.stackValue(level, s);
        return sum;
    }

    /** Ставка сгорела — очистить все ячейки. */
    public static void clear(UpgraderMenu menu) {
        ((FateMenuAccess) menu).fatewheel$input().setItem(0, ItemStack.EMPTY);
        Container extra = ((FateMenuAccess) menu).fatewheel$extra();
        for (int i = 0; i < extra.getContainerSize(); i++) extra.setItem(i, ItemStack.EMPTY);
        extra.setChanged();
    }

    /** «Алмаз x2, Золотой слиток x5». */
    public static String describe(List<ItemStack> stacks) {
        List<String> parts = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s.isEmpty()) continue;
            parts.add(s.getHoverName().getString() + (s.getCount() > 1 ? " x" + s.getCount() : ""));
        }
        return String.join(", ", parts);
    }

    public static List<ItemStack> copy(UpgraderMenu menu) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : stacks(menu)) out.add(s.copy());
        return out;
    }

    /** Наша дополнительная ячейка: как родная — нельзя трогать во время вращения и класть запрещённое. */
    public static final class StakeSlot extends Slot {
        private final BooleanSupplier spinning;

        public StakeSlot(Container container, int index, int x, int y, BooleanSupplier spinning) {
            super(container, index, x, y);
            this.spinning = spinning;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !spinning.getAsBoolean() && !ItemValues.isBlacklisted(stack.getItem());
        }

        @Override
        public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
            return !spinning.getAsBoolean();
        }
    }
}
