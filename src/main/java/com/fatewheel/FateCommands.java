package com.fatewheel;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /fate         — открыть Апгрейдер (ставки временем и органами — в нём)
 * /fate status  — что у вас отнято (навсегда, пока не отыграете)
 * /fate heal    — вернуть органы (только в творческом режиме)
 * /fate clear   — снять проклятия времени (только в творческом режиме)
 */
public final class FateCommands {
    private FateCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("fate")
                .executes(ctx -> {
                    FateManager.openUpgrader(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    p.sendSystemMessage(Component.literal("— Колесо судьбы —").withStyle(ChatFormatting.GOLD));
                    for (Component line : FateManager.statusLines(FateManager.get(p))) p.sendSystemMessage(line);
                    return 1;
                }))
                .then(Commands.literal("heal").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (!p.isCreative()) {
                        ctx.getSource().sendFailure(Component.literal("Только в творческом режиме."));
                        return 0;
                    }
                    FateManager.healBody(p);
                    p.sendSystemMessage(Component.literal("Органы возвращены.").withStyle(ChatFormatting.GREEN));
                    return 1;
                }))
                .then(Commands.literal("clear").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (!p.isCreative()) {
                        ctx.getSource().sendFailure(Component.literal("Только в творческом режиме."));
                        return 0;
                    }
                    FateManager.clearCurses(p);
                    p.sendSystemMessage(Component.literal("Проклятия времени сняты.").withStyle(ChatFormatting.GREEN));
                    return 1;
                })));
    }
}
