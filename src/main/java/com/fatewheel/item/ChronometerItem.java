package com.fatewheel.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;

/** Хронометр: ПКМ — следующее время суток, Shift+ПКМ — остановить / запустить время. */
public class ChronometerItem extends SpecialItem {
    private static final long[] MARKS = {1000, 6000, 12500, 18000};
    private static final String[] NAMES = {"Утро", "Полдень", "Закат", "Полночь"};

    public ChronometerItem(Properties props) {
        super(props, "ПКМ — перевести время: утро → полдень → закат → полночь.",
                "Shift+ПКМ — остановить или снова запустить время.");
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        Optional<Holder<WorldClock>> clock = sl.dimensionType().defaultClock();
        if (clock.isEmpty()) {
            say(player, "Здесь у времени нет суток.", ChatFormatting.GRAY);
            return InteractionResult.FAIL;
        }
        ServerClockManager clocks = sl.getServer().clockManager();
        if (player.isSecondaryUseActive()) {
            boolean pause = !clocks.getInstance(clock.get()).isPaused();
            clocks.setPaused(clock.get(), pause);
            say(player, pause ? "Время остановлено." : "Время снова идёт.", ChatFormatting.LIGHT_PURPLE);
        } else {
            long total = clocks.getInstance(clock.get()).totalTicks();
            long day = Math.floorDiv(total, 24000L), t = Math.floorMod(total, 24000L);
            int next = 0;
            while (next < MARKS.length && MARKS[next] <= t) next++;
            long target = next < MARKS.length ? day * 24000L + MARKS[next] : (day + 1) * 24000L + MARKS[0];
            clocks.setTotalTicks(clock.get(), target);
            say(player, "Хронометр: " + NAMES[next % MARKS.length], ChatFormatting.LIGHT_PURPLE);
        }
        sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.7f);
        player.getCooldowns().addCooldown(player.getItemInHand(hand), 20);
        return InteractionResult.SUCCESS;
    }
}
