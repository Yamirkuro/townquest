package org.example.yamirkuro.townquest.client;

import org.example.yamirkuro.townquest.quest.BookEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Останній знімок квестів з книги (для підсвітки зон з глобусом). */
public final class ClientQuestCache {

    private static List<BookEntry> entries = List.of();

    private ClientQuestCache() {}

    public static void set(List<BookEntry> list) {
        entries = List.copyOf(list == null ? List.of() : list);
    }

    public static List<BookEntry> all() {
        return entries;
    }
}
