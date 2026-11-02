package org.example.yamirkuro.townquest.quest;

public enum QuestKind {
    /** Прогрес рахується за предметами в прив'язаних сундуках. */
    ITEMS,
    /** Ручна перевірка: власник сам підтверджує виконання. */
    MANUAL;

    public QuestKind next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static QuestKind byName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return ITEMS;
        }
    }
}
