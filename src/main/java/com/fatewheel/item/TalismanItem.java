package com.fatewheel.item;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Талисман: пока лежит где угодно в инвентаре, владелец получает эффект. */
public class TalismanItem extends SpecialItem {
    private final Holder<MobEffect> effect;
    private final int amplifier;

    public TalismanItem(Properties props, Holder<MobEffect> effect, int amplifier, String... lines) {
        super(props, lines);
        this.effect = effect;
        this.amplifier = amplifier;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (!(owner instanceof Player p) || level.getGameTime() % 20 != 0) return;
        // ночное зрение мигает, если осталось меньше 10 секунд — держим запас
        int duration = effect == MobEffects.NIGHT_VISION ? 260 : 60;
        MobEffectInstance cur = p.getEffect(effect);
        if (cur != null && (cur.getAmplifier() > amplifier || cur.getDuration() > duration)) return;
        p.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
    }
}
