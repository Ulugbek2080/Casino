package com.fatewheel.upgrader;

import com.fatewheel.FateManager;
import com.fatewheel.FateState;
import com.fatewheel.Stake;
import com.fatewheel.network.FateSelectPayload;
import net.execheinz.upgrader.AttemptLimiter;
import net.execheinz.upgrader.Ids;
import net.execheinz.upgrader.UpgraderConfig;
import net.execheinz.upgrader.menu.UpgraderMenu;
import net.execheinz.upgrader.network.ClientboundUpgradeResultPacket;
import net.execheinz.upgrader.network.ClientboundUpgraderSyncPacket;
import net.execheinz.upgrader.network.UpgraderNetwork;
import net.execheinz.upgrader.value.ItemValues;
import net.execheinz.upgrader.value.UpgradeOdds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Серверная часть встраивания в Апгрейдер: в ячейке ставки и в ячейке цели вместо предмета
 * может быть время или органы. Вызывается из UpgraderMenuMixin.
 */
public final class FateUpgrader {
    /** Текущий выбор игрока в открытом Апгрейдере. */
    public static final class Selection {
        public Mode inMode = Mode.ITEMS;
        public int inMask;
        public Mode outMode = Mode.ITEMS;
        public int outMask;

        // что уже отправлено клиенту
        private String lastTarget;
        private long lastIn = -1, lastOut = -1;
        private int lastCount = -1, lastCooldown = -1;

        public boolean active() {
            return inMode != Mode.ITEMS || outMode != Mode.ITEMS;
        }
    }

    private record Spin(Mode inMode, int inMask, Mode outMode, int outMask, Item target, int count, boolean win) {
    }

    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final Map<UUID, Spin> SPINS = new HashMap<>();

    private FateUpgrader() {
    }

    public static Selection selection(Player p) {
        return SELECTIONS.computeIfAbsent(p.getUUID(), u -> new Selection());
    }

    private static Selection peek(Player p) {
        return SELECTIONS.get(p.getUUID());
    }

    // ------------------------------------------------------------------ выбор

    /** Клиент прислал новый выбор. */
    public static void onSelect(ServerPlayer p, FateSelectPayload msg) {
        if (!(p.containerMenu instanceof UpgraderMenu menu) || menu.isSpinning()) {
            FateManager.sendSync(p);
            return;
        }
        Selection s = selection(p);
        s.inMode = Mode.byId(msg.inMode());
        s.outMode = Mode.byId(msg.outMode());
        s.inMask = msg.inMask();
        s.outMask = msg.outMask();
        prune(s, FateManager.get(p));
        if (s.outMode != Mode.ITEMS && menu.getTarget() != null) {
            menu.setTarget(p, null); // справа теперь не предмет
        }
        FateManager.sendSync(p);
        ((FateMenuAccess) menu).fatewheel$sync(p);
    }

    /** Ставить можно только то, что есть; вернуть — только то, что потеряно. */
    private static void prune(Selection s, FateState st) {
        s.inMask &= s.inMode.mask() & ~Stake.unstakeable(st.lostMask);
        s.inMask &= ~Stake.childrenOf(s.inMask); // рука в ставке — кисть уходит вместе с ней
        s.outMask &= s.outMode.mask() & st.lostMask;
    }

    /** Окно Апгрейдера закрыто. */
    public static void closed(ServerPlayer p) {
        if (SELECTIONS.remove(p.getUUID()) != null) FateManager.sendSync(p);
    }

    public static void forget(UUID id) {
        SELECTIONS.remove(id);
        SPINS.remove(id);
    }

    // ------------------------------------------------------------------ ценности

    private static long inValue(UpgraderMenu menu, ServerPlayer p, Selection s) {
        if (s.inMode == Mode.ITEMS) return Math.round(ItemValues.stackValue(p.level(), menu.getInputStack()));
        return Stake.sumValue(s.inMask);
    }

    /** Ценность одной единицы цели (как показывает Upgrader). */
    private static long outUnitValue(UpgraderMenu menu, ServerPlayer p, Selection s) {
        if (s.outMode == Mode.ITEMS) {
            Item target = menu.getTarget();
            return target == null ? 0 : Math.round(ItemValues.unitValue(p.level(), target));
        }
        return Stake.sumRestore(s.outMask);
    }

    private static int outCount(UpgraderMenu menu, Selection s) {
        return s.outMode == Mode.ITEMS ? ((FateMenuAccess) menu).fatewheel$count() : 1;
    }

    // ------------------------------------------------------------------ хуки UpgraderMenu

    /** Вместо UpgraderMenu.syncToClient. Возвращает true, если отправили сами. */
    public static boolean sync(UpgraderMenu menu, ServerPlayer p, boolean force) {
        Selection s = peek(p);
        if (s == null || !s.active()) return false;
        String targetId = s.outMode == Mode.ITEMS && menu.getTarget() != null ? Ids.of(menu.getTarget()) : "";
        long in = inValue(menu, p, s);
        long out = outUnitValue(menu, p, s);
        int count = outCount(menu, s);
        int cooldown = AttemptLimiter.cooldownRemaining(p);
        if (force || !targetId.equals(s.lastTarget) || in != s.lastIn || out != s.lastOut
                || count != s.lastCount || cooldown != s.lastCooldown) {
            s.lastTarget = targetId;
            s.lastIn = in;
            s.lastOut = out;
            s.lastCount = count;
            s.lastCooldown = cooldown;
            float chance = (float) UpgradeOdds.chance(in, (double) out * count);
            UpgraderNetwork.toClient(p, new ClientboundUpgraderSyncPacket(targetId, count, in, out, chance, cooldown));
        }
        return true;
    }

    /** Вместо UpgraderMenu.startUpgrade. Возвращает true, если обработали сами. */
    public static boolean start(UpgraderMenu menu, ServerPlayer p) {
        Selection s = peek(p);
        if (s == null || !s.active()) return false;
        if (menu.isSpinning()) return true;
        FateState st = FateManager.get(p);
        prune(s, st);
        ItemStack input = menu.getInputStack();

        if (s.inMode == Mode.ITEMS) {
            if (input.isEmpty() || ItemValues.isBlacklisted(input.getItem())) return true;
        } else {
            if (s.inMask == 0) return fail(p, "Отметьте, что ставите.");
            if (!input.isEmpty()) return fail(p, "Уберите предмет из ячейки: сейчас ставка — " + s.inMode.title.toLowerCase() + ".");
        }
        if (s.outMode == Mode.ITEMS) {
            if (menu.getTarget() == null) return true;
        } else if (s.outMask == 0) {
            return fail(p, "Отметьте, что хотите вернуть.");
        }
        if (AttemptLimiter.cooldownRemaining(p) > 0) {
            sync(menu, p, true);
            return true;
        }

        AttemptLimiter.record(p);
        int count = outCount(menu, s);
        double chance = UpgradeOdds.chance(inValue(menu, p, s), (double) outUnitValue(menu, p, s) * count);
        RandomSource random = p.getRandom();
        boolean win = random.nextDouble() < chance;
        int ticks = Math.max(1, UpgraderConfig.spinTicks);
        ((FateMenuAccess) menu).fatewheel$begin(win, ticks);
        SPINS.put(p.getUUID(), new Spin(s.inMode, s.inMask, s.outMode, s.outMask, menu.getTarget(), count, win));
        UpgraderNetwork.toClient(p, new ClientboundUpgradeResultPacket(win, landingAngle(random, chance, win), ticks, (float) chance));
        return true;
    }

    private static boolean fail(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED), true);
        return true;
    }

    /** Вместо UpgraderMenu.applyResult. Возвращает true, если это было наше вращение. */
    public static boolean resolve(UpgraderMenu menu, Player player) {
        Spin spin = SPINS.remove(player.getUUID());
        if (spin == null || !(player instanceof ServerPlayer p)) return false;
        Container input = ((FateMenuAccess) menu).fatewheel$input();
        ItemStack staked = input.getItem(0).copy();

        // предмет в ячейке ставки уходит в любом случае — как в обычном Апгрейдере
        if (spin.inMode == Mode.ITEMS) input.setItem(0, ItemStack.EMPTY);

        if (spin.win) {
            Component got;
            if (spin.outMode == Mode.ITEMS && spin.target != null) {
                ItemStack prize = new ItemStack(spin.target, spin.count);
                ItemStack old = input.getItem(0);
                input.setItem(0, prize);
                if (!old.isEmpty()) FateManager.give(p, old); // на всякий случай: ничего не теряем
                got = Component.literal("Судьба отдаёт вам: ").append(prize.getHoverName())
                        .append(prize.getCount() > 1 ? " x" + prize.getCount() : "");
            } else {
                FateManager.restore(p, spin.outMask);
                got = Component.literal("Возвращено: " + String.join(", ", Stake.titles(spin.outMask)));
            }
            FateManager.announceWin(p, got, spin.inMode != Mode.ITEMS);
        } else {
            String lostText;
            if (spin.inMode == Mode.ITEMS) {
                lostText = staked.getHoverName().getString() + (staked.getCount() > 1 ? " x" + staked.getCount() : "");
            } else {
                FateManager.lose(p, spin.inMask);
                lostText = String.join(", ", Stake.titles(spin.inMask));
            }
            FateManager.announceLoss(p, lostText, spin.inMode != Mode.ITEMS);
        }
        input.setChanged();

        Selection s = peek(p);
        if (s != null) {
            prune(s, FateManager.get(p));
            FateManager.sendSync(p);
        }
        return true;
    }

    /** Копия UpgraderMenu.landingAngle: куда остановится стрелка. */
    private static float landingAngle(RandomSource random, double chance, boolean win) {
        float zone = (float) (chance * 360.0);
        float span = win ? zone : 360.0F - zone;
        float margin = Math.min(1.5F, span * 0.2F);
        float usable = Math.max(0.01F, span - margin * 2.0F);
        float offset = margin + random.nextFloat() * usable;
        return win ? offset : (zone + offset) % 360.0F;
    }
}
