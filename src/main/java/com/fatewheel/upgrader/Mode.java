package com.fatewheel.upgrader;

import com.fatewheel.Stake;

/** Что лежит в ячейке Апгрейдера: обычный предмет, время или органы. */
public enum Mode {
    ITEMS("Предметы"),
    TIME("Время"),
    ORGANS("Органы"),
    /** Только справа: вместо цели — колесо с секторами (см. Fortune). */
    FORTUNE("Колесо судьбы");

    public final String title;

    Mode(String title) {
        this.title = title;
    }

    /** Какие ставки допустимы в этом режиме. */
    public int mask() {
        return switch (this) {
            case ITEMS -> 0;
            case TIME -> Stake.TIME_MASK;
            case ORGANS -> Stake.BODY_MASK;
            case FORTUNE -> 0;
        };
    }

    public static Mode byId(int id) {
        Mode[] all = values();
        return id >= 0 && id < all.length ? all[id] : ITEMS;
    }
}
