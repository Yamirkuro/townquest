package org.example.yamirkuro.townquest.client;

import org.example.yamirkuro.townquest.quest.BookEntry;
import org.example.yamirkuro.townquest.quest.Quest;
import org.example.yamirkuro.townquest.quest.QuestStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Клієнтський трекер квестів для HUD (зірочка в книзі). */
public final class QuestTracker {

    public static final int MAX_TRACKED = 5;

    private static final Set<UUID> tracked = new LinkedHashSet<>();
    private static final Map<UUID, BookEntry> cache = new LinkedHashMap<>();

    private QuestTracker() {}

    public static boolean isTracked(UUID questId) {
        return tracked.contains(questId);
    }

    public static boolean toggle(UUID questId) {
        if (tracked.contains(questId)) {
            tracked.remove(questId);
            cache.remove(questId);
            return false;
        }
        if (tracked.size() >= MAX_TRACKED) {
            return false;
        }
        tracked.add(questId);
        return true;
    }

    public static void untrack(UUID questId) {
        tracked.remove(questId);
        cache.remove(questId);
    }

    /** Оновлення з даних книги (прогрес, назви). */
    public static void updateFromBook(List<BookEntry> entries) {
        cache.clear();
        for (BookEntry e : entries) {
            UUID id = e.quest().getId();
            if (tracked.contains(id)) {
                cache.put(id, e);
            }
        }
        // прибрати трек квестів, яких більше немає
        tracked.removeIf(id -> {
            for (BookEntry e : entries) {
                if (e.quest().getId().equals(id)) return false;
            }
            return true;
        });
    }

    public static List<BookEntry> trackedEntries() {
        List<BookEntry> list = new ArrayList<>();
        for (UUID id : tracked) {
            BookEntry e = cache.get(id);
            if (e != null && e.quest().getStatus() != QuestStatus.DONE) {
                list.add(e);
            }
        }
        return list;
    }

    public static int count() {
        return tracked.size();
    }
}
