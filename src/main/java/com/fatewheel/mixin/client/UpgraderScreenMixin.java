package com.fatewheel.mixin.client;

import com.fatewheel.client.FateScreenUi;
import com.fatewheel.upgrader.FateScreenAccess;
import net.execheinz.upgrader.client.StyledButton;
import net.execheinz.upgrader.client.UpgraderScreen;
import net.execheinz.upgrader.menu.UpgraderMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Клиентская часть: кнопка выбора ставки, панели «Время» и «Органы» в экране Апгрейдера. */
@Mixin(UpgraderScreen.class)
public abstract class UpgraderScreenMixin extends AbstractContainerScreen<UpgraderMenu> implements FateScreenAccess {
    @Shadow
    private long inputValue;
    @Shadow
    private long targetValue;
    @Shadow
    private int cooldownSeconds;
    @Shadow
    private boolean spinning;
    @Shadow
    private boolean pickerOpen;
    @Shadow
    private StyledButton upgradeButton;
    @Shadow
    @Final
    private List<StyledButton> presets;

    private UpgraderScreenMixin(UpgraderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 288, 240);
    }

    @Override
    public long fatewheel$inputValue() {
        return this.inputValue;
    }

    @Override
    public long fatewheel$targetValue() {
        return this.targetValue;
    }

    @Override
    public boolean fatewheel$spinning() {
        return this.spinning;
    }

    @Override
    public boolean fatewheel$pickerOpen() {
        return this.pickerOpen;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void fatewheel$init(CallbackInfo ci) {
        FateScreenUi.onInit();
    }

    /** Перед отрисовкой кнопок: «ПРОКАЧАТЬ» доступна и для ставок временем/органами. */
    @Inject(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void fatewheel$buttons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        FateScreenUi.adjustButtons(this.menu, this.upgradeButton, this.presets,
                !this.spinning && !this.pickerOpen && this.cooldownSeconds <= 0);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fatewheel$render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!this.pickerOpen) FateScreenUi.render(this, this, graphics, mouseX, mouseY);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void fatewheel$scroll(double mx, double my, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (!this.pickerOpen && FateScreenUi.scroll(this, mx, my, dy)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void fatewheel$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!this.pickerOpen && FateScreenUi.click(this, this, event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }
}
