package com.fatewheel.network;

import com.fatewheel.FateWheel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Сервер → клиент: чем закончилось вращение Колеса судьбы (показывается прямо в окне Апгрейдера). */
public record FateFortunePayload(int tier, ItemStack prize, int restoreMask, boolean found) implements CustomPacketPayload {
    public static final Type<FateFortunePayload> TYPE = new Type<>(FateWheel.id("fortune"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FateFortunePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FateFortunePayload::tier,
            ItemStack.OPTIONAL_STREAM_CODEC, FateFortunePayload::prize,
            ByteBufCodecs.VAR_INT, FateFortunePayload::restoreMask,
            ByteBufCodecs.BOOL, FateFortunePayload::found,
            FateFortunePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
