package com.fatewheel;

import com.fatewheel.upgrader.Mode;

/**
 * То, что сервер присылает клиенту. Класс без клиентских импортов, поэтому его безопасно
 * трогать из обработчика пакета и из общих событий на любой стороне.
 */
public final class ClientFlags {
    /** Всё проигранное (Stake.bit). */
    public static volatile int lostMask = 0;

    /** Выбор в Апгрейдере: что ставим (слева) и что хотим (справа). */
    public static volatile int inMode = Mode.ITEMS.ordinal();
    public static volatile int inMask = 0;
    public static volatile int outMode = Mode.ITEMS.ordinal();
    public static volatile int outMask = 0;

    private ClientFlags() {
    }

    public static boolean lost(Stake s) {
        return (lostMask & s.bit) != 0;
    }

    public static boolean past() {
        return lost(Stake.PAST);
    }

    public static void reset() {
        lostMask = 0;
        inMode = Mode.ITEMS.ordinal();
        inMask = 0;
        outMode = Mode.ITEMS.ordinal();
        outMask = 0;
    }
}
