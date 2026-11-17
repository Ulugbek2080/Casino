package com.fatewheel.upgrader;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;

/** Доступ к приватным частям UpgraderMenu. Реализуется mixin-ом UpgraderMenuMixin. */
public interface FateMenuAccess {
    /** Запустить вращение с заранее решённым исходом. */
    void fatewheel$begin(boolean win, int ticks);

    /** Ячейка ставки. */
    Container fatewheel$input();

    /** Наши 4 дополнительные ячейки ставки (см. StakeSlots). */
    Container fatewheel$extra();

    /** Сколько целевых предметов выбрано (с учётом ограничений Upgrader). */
    int fatewheel$count();

    /** Вернуть цель и количество с прошлого открытия (без пересылки — уйдёт с первой синхронизацией). */
    void fatewheel$restoreTarget(net.minecraft.world.item.Item target, int count);

    /** Принудительно переслать клиенту ценности и шанс. */
    void fatewheel$sync(ServerPlayer player);
}
