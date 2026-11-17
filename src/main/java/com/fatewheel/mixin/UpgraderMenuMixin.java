package com.fatewheel.mixin;

import com.fatewheel.upgrader.FateMenuAccess;
import com.fatewheel.upgrader.FateUpgrader;
import com.fatewheel.upgrader.StakeSlots;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.execheinz.upgrader.menu.UpgraderMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Серверная часть: ставки временем и органами внутри меню Апгрейдера. */
@Mixin(UpgraderMenu.class)
public abstract class UpgraderMenuMixin extends AbstractContainerMenu implements FateMenuAccess {
    /** Индексы наших ячеек в меню: после родной (0) и инвентаря (1..36). */
    @Unique
    private static final int FATEWHEEL$EXTRA_START = 37;

    @Unique
    private final SimpleContainer fatewheel$extra = new SimpleContainer(StakeSlots.EXTRA);

    private UpgraderMenuMixin(MenuType<?> type, int id) {
        super(type, id);
    }

    @Shadow
    private int spinTicksLeft;
    @Shadow
    private boolean pendingWin;
    @Shadow
    private int targetCount;
    @Shadow
    private Item target;
    @Shadow
    @Final
    private Container input;
    @Shadow
    @Final
    private Player player;

    @Shadow
    private int clampCount(int count) {
        throw new AssertionError();
    }

    @Shadow
    private void syncToClient(ServerPlayer serverPlayer, boolean force) {
        throw new AssertionError();
    }

    @Override
    public void fatewheel$begin(boolean win, int ticks) {
        this.pendingWin = win;
        this.spinTicksLeft = ticks;
    }

    @Override
    public Container fatewheel$input() {
        return this.input;
    }

    @Override
    public Container fatewheel$extra() {
        return this.fatewheel$extra;
    }

    @Override
    public int fatewheel$count() {
        return this.clampCount(this.targetCount);
    }

    @Override
    public void fatewheel$restoreTarget(Item item, int count) {
        this.target = item;
        this.targetCount = Math.max(1, count);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void fatewheel$opened(int id, Inventory inventory, CallbackInfo ci) {
        UpgraderMenu self = (UpgraderMenu) (Object) this;
        for (int i = 0; i < StakeSlots.EXTRA; i++) {
            this.addSlot(new StakeSlots.StakeSlot(this.fatewheel$extra, i, StakeSlots.X[i], StakeSlots.Y, self::isSpinning));
        }
        if (this.player instanceof ServerPlayer sp) FateUpgrader.opened((UpgraderMenu) (Object) this, sp);
    }

    @Inject(method = "setTarget", at = @At("TAIL"))
    private void fatewheel$targetSet(ServerPlayer serverPlayer, Item item, CallbackInfo ci) {
        FateUpgrader.targetChanged((UpgraderMenu) (Object) this, serverPlayer);
    }

    @Inject(method = "setCount", at = @At("TAIL"))
    private void fatewheel$countSet(ServerPlayer serverPlayer, int count, CallbackInfo ci) {
        FateUpgrader.targetChanged((UpgraderMenu) (Object) this, serverPlayer);
    }

    @Override
    public void fatewheel$sync(ServerPlayer serverPlayer) {
        this.syncToClient(serverPlayer, true);
    }

    @Inject(method = "startUpgrade", at = @At("HEAD"), cancellable = true)
    private void fatewheel$startUpgrade(ServerPlayer serverPlayer, CallbackInfo ci) {
        if (FateUpgrader.start((UpgraderMenu) (Object) this, serverPlayer)) ci.cancel();
    }

    @Inject(method = "applyResult", at = @At("HEAD"), cancellable = true)
    private void fatewheel$applyResult(CallbackInfo ci) {
        if (FateUpgrader.resolve((UpgraderMenu) (Object) this, this.player)) ci.cancel();
    }

    @Inject(method = "syncToClient", at = @At("HEAD"), cancellable = true)
    private void fatewheel$syncToClient(ServerPlayer serverPlayer, boolean force, CallbackInfo ci) {
        if (FateUpgrader.sync((UpgraderMenu) (Object) this, serverPlayer, force)) ci.cancel();
    }

    /** Shift+клик: из наших ячеек — в инвентарь; из инвентаря — в наши, если родная ячейка занята. */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void fatewheel$quickMove(Player p, int index, CallbackInfoReturnable<ItemStack> cir) {
        UpgraderMenu self = (UpgraderMenu) (Object) this;
        if (index < 0 || index >= this.slots.size()) return;
        Slot slot = this.slots.get(index);
        if (self.isSpinning() || !slot.hasItem()) {
            if (index >= FATEWHEEL$EXTRA_START) cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        int from, to;
        if (index >= FATEWHEEL$EXTRA_START) {
            from = 1;
            to = FATEWHEEL$EXTRA_START;
        } else if (index >= 1 && !self.getInputStack().isEmpty()) {
            from = FATEWHEEL$EXTRA_START;
            to = FATEWHEEL$EXTRA_START + StakeSlots.EXTRA;
        } else {
            return; // родная ячейка или она пуста — пусть Апгрейдер сделает как обычно
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!this.moveItemStackTo(stack, from, to, false)) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        cir.setReturnValue(copy);
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void fatewheel$removed(Player p, CallbackInfo ci) {
        this.clearContainer(p, this.fatewheel$extra); // вернуть вещи из наших ячеек
        if (p instanceof ServerPlayer sp) FateUpgrader.closed(sp);
    }
}
