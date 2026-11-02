package org.example.yamirkuro.townquest.quest;

public enum QuestStatus {
    OPEN,
    IN_PROGRESS,
    REVIEW,
    DONE;

    public static QuestStatus byName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return OPEN;
        }
    }
}
