package org.example.yamirkuro.townquest.quest;

public enum QuestType {
    GLOBAL,
    PERSONAL,
    GROUP;

    public QuestType next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static QuestType byName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return GLOBAL;
        }
    }
}
