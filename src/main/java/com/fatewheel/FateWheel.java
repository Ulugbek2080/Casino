package com.fatewheel;

import com.fatewheel.network.FateSelectPayload;
import com.fatewheel.network.FateSyncPayload;
import com.fatewheel.upgrader.FateUpgrader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * «Колесо судьбы» — аддон к Upgrader: в Апгрейдере вместо предмета можно поставить
 * время (Прошлое / Настоящее / Будущее) или органы, и так же отыграть их обратно.
 */
@Mod(FateWheel.MODID)
public final class FateWheel {
    public static final String MODID = "fatewheel";

    public FateWheel(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.LOCAL, FateConfig.SPEC);
        modBus.addListener(FateWheel::onRegisterPayloads);
        FateItems.register(modBus);
        FateManager.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(FateCommands::onRegisterCommands);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("2");
        r.playToClient(FateSyncPayload.TYPE, FateSyncPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            ClientFlags.lostMask = payload.lostMask();
            ClientFlags.inMode = payload.inMode();
            ClientFlags.inMask = payload.inMask();
            ClientFlags.outMode = payload.outMode();
            ClientFlags.outMask = payload.outMask();
        }));
        r.playToServer(FateSelectPayload.TYPE, FateSelectPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) FateUpgrader.onSelect(sp, payload);
        }));
    }
}
