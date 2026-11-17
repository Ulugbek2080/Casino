package com.fatewheel.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

/** Песочные часы роста: меняют random_tick_speed — как быстро растут растения, тает лёд и т.д. */
public class GrowthHourglassItem extends SpecialItem {
    private static final int[] STEPS = {0, 1, 3, 6, 10, 20, 50, 100, 200, 500};

    public GrowthHourglassItem(Properties props) {
        super(props, "ПКМ — мир живёт быстрее, Shift+ПКМ — медленнее.",
                "Меняет скорость случайных тиков (random_tick_speed): 0…500, обычная — 3.");
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        int now = sl.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        boolean slower = player.isSecondaryUseActive();
        int next = now;
        if (slower) {
            for (int k = STEPS.length - 1; k >= 0; k--) if (STEPS[k] < now) { next = STEPS[k]; break; }
        } else {
            for (int step : STEPS) if (step > now) { next = step; break; }
        }
        if (next == now) {
            say(player, slower ? "Медленнее уже некуда." : "Быстрее уже некуда.", ChatFormatting.GRAY);
            return InteractionResult.FAIL;
        }
        sl.getGameRules().set(GameRules.RANDOM_TICK_SPEED, next, sl.getServer());
        Component msg = Component.literal(player.getName().getString() + " переворачивает песочные часы: скорость роста мира — "
                + next + (next == 3 ? " (обычная)" : "")).withStyle(ChatFormatting.GREEN);
        for (ServerPlayer other : sl.getServer().getPlayerList().getPlayers()) other.sendSystemMessage(msg);
        sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f,
                slower ? 0.6f : 1.4f);
        player.getCooldowns().addCooldown(player.getItemInHand(hand), 20);
        return InteractionResult.SUCCESS;
    }
}
