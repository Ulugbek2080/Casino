package com.fatewheel.item;

import com.fatewheel.FateWheel;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Жезл размера: ПКМ — вырасти, Shift+ПКМ — уменьшиться (атрибут scale, как /attribute ... scale). */
public class SizeWandItem extends SpecialItem {
    private static final Identifier MOD = FateWheel.id("size_wand");
    private static final double[] STEPS = {0.25, 0.4, 0.6, 0.8, 1.0, 1.5, 2.0, 3.0, 4.0};

    public SizeWandItem(Properties props) {
        super(props, "ПКМ — стать больше, Shift+ПКМ — меньше.",
                "ПКМ по существу или игроку — изменить его размер.", "От 0.25 до 4 размеров. Остаётся после смерти.");
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return resize(player, player, hand) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** ПКМ по существу: меняем размер ему (игроку, мобу, животному). */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        return resize(player, target, hand) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    private static boolean resize(Player player, LivingEntity target, InteractionHand hand) {
        Level level = player.level();
        boolean self = target == player;
        String who = self ? "" : target.getName().getString() + ": ";
        AttributeInstance scale = target.getAttribute(Attributes.SCALE);
        if (scale == null) {
            say(player, "Этого не изменить.", ChatFormatting.GRAY);
            return false;
        }
        AttributeModifier cur = scale.getModifier(MOD);
        double now = cur == null ? 1.0 : 1.0 + cur.amount();
        int i = 0;
        for (int k = 0; k < STEPS.length; k++) if (Math.abs(STEPS[k] - now) < Math.abs(STEPS[i] - now)) i = k;
        boolean shrink = player.isSecondaryUseActive();
        int next = Math.max(0, Math.min(STEPS.length - 1, i + (shrink ? -1 : 1)));
        if (next == i) {
            say(player, who + (shrink ? "меньше уже некуда." : "больше уже некуда."), ChatFormatting.GRAY);
            return false;
        }
        scale.removeModifier(MOD);
        if (STEPS[next] != 1.0) {
            scale.addPermanentModifier(new AttributeModifier(MOD, STEPS[next] - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        say(player, who + "размер x" + STEPS[next], ChatFormatting.AQUA);
        if (!self && target instanceof ServerPlayer victim) {
            victim.sendSystemMessage(Component.literal(player.getName().getString() + " меняет ваш размер: x" + STEPS[next])
                    .withStyle(ChatFormatting.AQUA), true);
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                shrink ? SoundEvents.PUFFER_FISH_BLOW_OUT : SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.PLAYERS, 1f, 1f);
        player.getCooldowns().addCooldown(player.getItemInHand(hand), 10);
        return true;
    }
}
