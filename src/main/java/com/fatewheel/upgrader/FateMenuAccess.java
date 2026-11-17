package com.fatewheel.upgrader;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;

/** Доступ к приватным частям UpgraderMenu. Реализуется mixin-ом UpgraderMenuMixin. */
public interface FateMenuAccess {
    /** Запустить вращение с заранее решённым исходом. */
    void fatewheel$begin(boolean win, int ticks);

    /** Ячейка ставки. */
    Container fatewheel$input();

    /** Сколько целевых предметов выбрано (с учётом ограничений Upgrader). */
    int fatewheel$count();

    /** Принудительно переслать клиенту ценности и шанс. */
    void fatewheel$sync(ServerPlayer player);
}
