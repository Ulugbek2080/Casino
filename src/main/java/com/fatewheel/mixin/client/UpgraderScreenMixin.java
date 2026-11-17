package com.fatewheel.mixin.client;

import com.fatewheel.client.FateScreenUi;
import com.fatewheel.upgrader.FateScreenAccess;
import com.fatewheel.upgrader.StakeSlots;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    @Shadow
    private static void slot(GuiGraphicsExtractor graphics, int x, int y) {
        throw new AssertionError();
    }

    @Shadow
    private static String format(long value) {
        throw new AssertionError();
    }

    @Shadow
    private int wrapped(GuiGraphicsExtractor graphics, Component text, int cx, int y, int width, int color) {
        throw new AssertionError();
    }

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

    /** Рамки наших 4 ячеек ставки (под родной). */
    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void fatewheel$slots(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        for (int x : StakeSlots.X) slot(graphics, this.leftPos + x, this.topPos + StakeSlots.Y);
    }

    /**
     * Текст карточек — как в Апгрейдере, но слева ниже: под родной ячейкой теперь ряд наших,
     * а ценность — сумма всех пяти.
     */
    @Inject(method = "renderCardText", at = @At("HEAD"), cancellable = true)
    private void fatewheel$cardText(GuiGraphicsExtractor g, CallbackInfo ci) {
        ci.cancel();
        int left = this.leftPos, top = this.topPos;
        int lx = left + 8 + 41, rx = left + 198 + 41;
        if (StakeSlots.empty(this.menu)) {
            g.centeredText(this.font, Component.literal("Положите ставку"), lx, top + 76, -1643790);
            g.centeredText(this.font, Component.literal("до 5 предметов"), lx, top + 87, -7892829);
        } else {
            g.centeredText(this.font, Component.translatable("gui.upgrader.value", format(this.inputValue)), lx, top + 76, -7892829);
        }
        Item target = this.menu.getTarget();
        if (target == null) {
            int y = wrapped(g, Component.translatable("gui.upgrader.pick_target"), rx, top + 56, 72, -1643790);
            wrapped(g, Component.translatable("gui.upgrader.pick_target_hint"), rx, y + 3, 72, -7892829);
        } else {
            g.item(new ItemStack(target), left + 230 + 1, top + 34 + 1);
            g.centeredText(this.font, Component.translatable("gui.upgrader.value", format(this.targetValue)), rx, top + 60, -7892829);
        }
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
