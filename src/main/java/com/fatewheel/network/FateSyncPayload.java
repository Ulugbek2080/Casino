package com.fatewheel.network;

import com.fatewheel.FateWheel;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Сервер → клиент: что проиграно и что выбрано в Апгрейдере. */
public record FateSyncPayload(int lostMask, int inMode, int inMask, int outMode, int outMask) implements CustomPacketPayload {
    public static final Type<FateSyncPayload> TYPE = new Type<>(FateWheel.id("sync"));

    public static final StreamCodec<ByteBuf, FateSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FateSyncPayload::lostMask,
            ByteBufCodecs.VAR_INT, FateSyncPayload::inMode,
            ByteBufCodecs.VAR_INT, FateSyncPayload::inMask,
            ByteBufCodecs.VAR_INT, FateSyncPayload::outMode,
            ByteBufCodecs.VAR_INT, FateSyncPayload::outMask,
            FateSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
