package com.fatewheel.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Особый предмет «Колеса судьбы»: светится, не стакается, описание — в подсказке (см. FateWheelClient). */
public class SpecialItem extends Item {
    public final List<String> lines;

    public SpecialItem(Properties props, String... lines) {
        super(props);
        this.lines = List.of(lines);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    protected static void say(Player p, String text, ChatFormatting color) {
        if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(text).withStyle(color), true);
    }
}
