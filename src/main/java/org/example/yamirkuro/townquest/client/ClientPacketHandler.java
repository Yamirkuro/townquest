package org.example.yamirkuro.townquest.client;

import net.minecraft.client.Minecraft;
import org.example.yamirkuro.townquest.client.screen.QuestBookScreen;
import org.example.yamirkuro.townquest.quest.BookEntry;

import java.util.List;

/** Клієнтські обробники пакетів. Окремий клас, щоб сервер не завантажував клієнтський код. */
public class ClientPacketHandler {

    public static void onBookData(boolean open, List<BookEntry> entries) {
        ClientQuestCache.set(entries);
        QuestTracker.updateFromBook(entries);

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof QuestBookScreen screen) {
            screen.setEntries(entries);
        } else if (open) {
            mc.setScreen(new QuestBookScreen(entries));
        }
    }
}
