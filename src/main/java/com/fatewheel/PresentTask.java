package com.fatewheel;

import java.util.List;

/** Задания проклятия «Настоящее». */
public enum PresentTask {
    BREAK("Сломай %d блоков", 4, 8),
    PLACE("Поставь %d блоков", 4, 8),
    JUMP("Подпрыгни %d раз", 8, 15),
    CROUCH("Присядь %d раз", 6, 12),
    WALK("Пройди %d блоков", 40, 80),
    CLIMB("Поднимись на %d блоков выше, чем сейчас", 4, 7),
    STILL("Не двигайся %d секунд", 5, 8),
    LOOK_UP("Посмотри прямо в небо", 1, 1),
    EAT("Съешь что-нибудь", 1, 1),
    KILL("Убей любое существо", 1, 1),
    TOSS("Выброси любой предмет", 1, 1),
    SAY("Напиши в чат слово «%s»", 1, 1);

    public static final List<String> WORDS = List.of(
            "прости", "память", "время", "судьба", "успею", "колесо", "ещё", "сейчас", "живой");

    public final String text;
    public final int min;
    public final int max;

    PresentTask(String text, int min, int max) {
        this.text = text;
        this.min = min;
        this.max = max;
    }

    public static PresentTask byId(int id) {
        PresentTask[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }

    public String describe(FateState s) {
        if (this == SAY) return String.format(text, s.taskWord);
        if (text.contains("%d")) return String.format(text, s.taskGoal);
        return text;
    }

    /** Можно ли выдать это задание игроку с такими потерями. */
    public boolean possible(FateState s) {
        boolean legs = s.lostLegs() < 2;
        boolean arms = !s.noArms();
        return switch (this) {
            case JUMP, CLIMB, WALK -> legs;
            case BREAK, KILL -> arms;
            // Настоящее + Будущее: ставить и использовать предметы нельзя
            case PLACE, EAT -> arms && !s.future();
            case SAY -> !s.lost(Stake.TONGUE);
            default -> true;
        };
    }
}
