package com.fatewheel.mixin;

import com.fatewheel.upgrader.FateMenuAccess;
import com.fatewheel.upgrader.FateUpgrader;
import net.execheinz.upgrader.menu.UpgraderMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Серверная часть: ставки временем и органами внутри меню Апгрейдера. */
@Mixin(UpgraderMenu.class)
public abstract class UpgraderMenuMixin implements FateMenuAccess {
    @Shadow
    private int spinTicksLeft;
    @Shadow
    private boolean pendingWin;
    @Shadow
    private int targetCount;
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
    public int fatewheel$count() {
        return this.clampCount(this.targetCount);
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

    @Inject(method = "removed", at = @At("TAIL"))
    private void fatewheel$removed(Player p, CallbackInfo ci) {
        if (p instanceof ServerPlayer sp) FateUpgrader.closed(sp);
    }
}
