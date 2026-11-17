package com.fatewheel.client;

import com.fatewheel.ClientFlags;
import com.fatewheel.FateWheel;
import com.fatewheel.Stake;
import com.fatewheel.item.SpecialItem;
import net.execheinz.upgrader.client.UpgraderScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Клиентская часть: «Прошлое» (не видно вещей), глаза (затемнение) и уши (глухота).
 * Всё остальное (задания, части тела, комбо) работает на сервере.
 */
@Mod(value = FateWheel.MODID, dist = Dist.CLIENT)
public final class FateWheelClient {
    /** Цвет «пустоты» поверх слотов. */
    private static final int FOG = 0xFF6E6E6E;
    private static final int FOG_EDGE = 0xFF555555;

    public FateWheelClient(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.addListener(FateWheelClient::onTooltip);
        NeoForge.EVENT_BUS.addListener(FateWheelClient::onScreenRender);
        NeoForge.EVENT_BUS.addListener(FateWheelClient::onHudPre);
        NeoForge.EVENT_BUS.addListener(FateWheelClient::onHudPost);
        NeoForge.EVENT_BUS.addListener(FateWheelClient::onRenderHand);
        NeoForge.EVENT_BUS.addListener(FateWheelClient::onLogout);
        NeoForge.EVENT_BUS.addListener(FateWheelClient::onEyes);
        NeoForge.EVENT_BUS.addListener(FateWheelClient::onSound);
    }

    /** В Апгрейдере подсказки оставляем, иначе нельзя будет играть. */
    private static boolean inWheelMenu() {
        return Minecraft.getInstance().gui.screen() instanceof UpgraderScreen;
    }

    private static void onTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().getItem() instanceof SpecialItem special) {
            for (String line : special.lines) event.getToolTip().add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        if (!ClientFlags.past() || inWheelMenu()) return;
        event.getToolTip().clear();
        event.getToolTip().add(Component.literal("??????????").withStyle(ChatFormatting.GRAY, ChatFormatting.OBFUSCATED));
        event.getToolTip().add(Component.literal("Вы не помните, что это.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** Закрываем «туманом» все слоты, принадлежащие инвентарю игрока, в любом открытом окне. */
    private static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!ClientFlags.past()) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        GuiGraphicsExtractor g = event.getGuiGraphics();
        g.nextStratum(); // рисуем поверх уже отрисованных предметов
        int left = screen.getLeftPos();
        int top = screen.getTopPos();
        for (Slot slot : screen.getMenu().slots) {
            if (!(slot.container instanceof Inventory)) continue;
            if (!slot.hasItem()) continue;
            int x = left + slot.x;
            int y = top + slot.y;
            g.fill(x, y, x + 16, y + 16, FOG_EDGE);
            g.fill(x + 1, y + 1, x + 15, y + 15, FOG);
        }
    }

    /** Прячем название выбранного предмета над хотбаром. */
    private static void onHudPre(RenderGuiLayerEvent.Pre event) {
        if (ClientFlags.past() && event.getName().equals(VanillaGuiLayers.SELECTED_ITEM_NAME)) {
            event.setCanceled(true);
        }
    }

    /** Закрываем иконки в хотбаре. */
    private static void onHudPost(RenderGuiLayerEvent.Post event) {
        if (!ClientFlags.past() || !event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphicsExtractor g = event.getGuiGraphics();
        g.nextStratum();
        int cx = g.guiWidth() / 2;
        int y = g.guiHeight() - 19;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) continue;
            int x = cx - 90 + i * 20 + 2;
            g.fill(x, y, x + 16, y + 16, FOG_EDGE);
            g.fill(x + 1, y + 1, x + 15, y + 15, FOG);
        }
    }

    /** В руке тоже ничего не разглядеть. */
    private static void onRenderHand(RenderHandEvent event) {
        if (ClientFlags.past()) event.setCanceled(true);
    }

    /** Без уха не слышно звуков с его стороны, без обоих — ничего, кроме звуков интерфейса. */
    private static void onSound(PlaySoundEvent event) {
        boolean left = ClientFlags.lost(Stake.LEFT_EAR), right = ClientFlags.lost(Stake.RIGHT_EAR);
        if (!left && !right) return;
        SoundInstance sound = event.getSound();
        if (sound == null || sound.getSource() == SoundSource.UI) return;
        if (left && right) {
            event.setSound(null);
            return;
        }
        // музыка и прочие «внутри головы» звуки без позиции слышны одним ухом
        if (sound.isRelative() || sound.getAttenuation() == SoundInstance.Attenuation.NONE) return;
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        double dx = sound.getX() - p.getX(), dz = sound.getZ() - p.getZ();
        double yaw = Math.toRadians(p.getYRot());
        // вектор «вправо» от взгляда игрока
        double side = dx * -Math.cos(yaw) + dz * -Math.sin(yaw);
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1.5) return; // свои шаги и звуки вплотную слышны обоими костями черепа
        if (right && side > dist * 0.25 || left && side < -dist * 0.25) event.setSound(null);
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientFlags.reset();
    }

    /** Без одного глаза темнеет четверть экрана с его стороны. Без обоих — слепота (эффект с сервера). */
    private static void onEyes(RenderGuiEvent.Pre event) {
        boolean left = ClientFlags.lost(Stake.LEFT_EYE), right = ClientFlags.lost(Stake.RIGHT_EYE);
        if (!left && !right) return;
        if (left && right) return;
        GuiGraphicsExtractor g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int quarter = w / 4;
        int fade = Math.max(8, w / 40);
        if (left) shade(g, 0, quarter, fade, h, true);
        if (right) shade(g, w - quarter, w, fade, h, false);
    }

    /** Тёмная полоса от x1 до x2 с мягким краем шириной fade со стороны центра экрана. */
    private static void shade(GuiGraphicsExtractor g, int x1, int x2, int fade, int h, boolean fadeOnRight) {
        int solidFrom = fadeOnRight ? x1 : x1 + fade;
        int solidTo = fadeOnRight ? x2 - fade : x2;
        g.fill(solidFrom, 0, solidTo, h, 0xF5000000);
        for (int i = 0; i < fade; i++) {
            int alpha = (int) (0xF5 * (1 - (i + 1) / (double) (fade + 1)));
            int x = fadeOnRight ? x2 - fade + i : x1 + fade - 1 - i;
            g.fill(x, 0, x + 1, h, alpha << 24);
        }
    }
}
