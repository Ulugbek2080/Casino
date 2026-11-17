package com.fatewheel.network;

import com.fatewheel.FateWheel;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Клиент → сервер: игрок выбрал в Апгрейдере, что ставит и что хочет. */
public record FateSelectPayload(int inMode, int inMask, int outMode, int outMask) implements CustomPacketPayload {
    public static final Type<FateSelectPayload> TYPE = new Type<>(FateWheel.id("select"));

    public static final StreamCodec<ByteBuf, FateSelectPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FateSelectPayload::inMode,
            ByteBufCodecs.VAR_INT, FateSelectPayload::inMask,
            ByteBufCodecs.VAR_INT, FateSelectPayload::outMode,
            ByteBufCodecs.VAR_INT, FateSelectPayload::outMask,
            FateSelectPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
