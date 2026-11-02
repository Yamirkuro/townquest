package org.example.yamirkuro.townquest.service;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import org.example.yamirkuro.townquest.block.entity.QuestTableBlockEntity;
import org.example.yamirkuro.townquest.data.BoundContainer;
import org.example.yamirkuro.townquest.data.QuestRegistry;
import org.example.yamirkuro.townquest.data.TableSnapshot;
import org.example.yamirkuro.townquest.network.ModNetwork;
import org.example.yamirkuro.townquest.network.packet.BookDataPacket;
import org.example.yamirkuro.townquest.quest.BookEntry;
import org.example.yamirkuro.townquest.quest.Quest;
import org.example.yamirkuro.townquest.quest.QuestStatus;
import org.example.yamirkuro.townquest.quest.QuestType;
import org.example.yamirkuro.townquest.registry.ModItems;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Серверна логіка Quest Book: що бачить гравець і дії «взяти», «відмовитись», «завершити». */
public final class QuestBookService {

    private QuestBookService() {}

    public static boolean hasBook(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (ItemStack stack : inv.items) {
            if (stack.is(ModItems.QUEST_BOOK.get())) return true;
        }
        for (ItemStack stack : inv.offhand) {
            if (stack.is(ModItems.QUEST_BOOK.get())) return true;
        }
        return false;
    }

    /** Глобальні квести бачать усі, особисті лише той, кому призначені. */
    public static boolean isVisibleTo(Quest quest, UUID player) {
        return switch (quest.getType()) {
            case GLOBAL -> true;
            case PERSONAL -> player.equals(quest.getAssignee());
            case GROUP -> quest.isGroupMember(player);
        };
    }

    public static List<BookEntry> collect(ServerPlayer player) {
        List<BookEntry> result = new ArrayList<>();
        QuestRegistry registry = QuestRegistry.get(player.server);

        for (Map.Entry<BoundContainer, TableSnapshot> e : registry.allSnapshots().entrySet()) {
            BoundContainer key = e.getKey();
            TableSnapshot snap = e.getValue();

            String ownerName = snap.ownerName();
            List<BlockPos> chests = snap.chestPositions();
            List<Quest> quests = snap.quests();

            QuestTableBlockEntity live = tryLoad(player, key);
            if (live != null) {
                ownerName = live.resolveOwnerName();
                chests = live.getContainerPositions();
                quests = live.getQuests();
            }

            for (Quest quest : quests) {
                if (isVisibleTo(quest, player.getUUID())) {
                    result.add(new BookEntry(key, quest, ownerName, chests));
                }
            }
        }

        result.sort(Comparator
                .comparing((BookEntry entry) -> entry.quest().getStatus() == QuestStatus.DONE)
                .thenComparing(entry -> entry.quest().getTitle().toLowerCase(Locale.ROOT)));
        return result;
    }

    private static QuestTableBlockEntity tryLoad(ServerPlayer player, BoundContainer key) {
        ServerLevel level = player.server.getLevel(key.dimension());
        if (level == null || !level.isLoaded(key.pos())) return null;
        if (level.getBlockEntity(key.pos()) instanceof QuestTableBlockEntity table) {
            return table;
        }
        return null;
    }

    /** open = true відкриває книгу, false лише оновлює вже відкриту. */
    public static void send(ServerPlayer player, boolean open) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new BookDataPacket(open, collect(player)));
    }

    public static void take(ServerPlayer player, BoundContainer key, UUID questId) {
        QuestTableBlockEntity table = loadedTable(player, key);
        if (table == null) return;

        Quest quest = table.findQuest(questId);
        if (quest == null || !isVisibleTo(quest, player.getUUID())) return;

        boolean already = quest.hasParticipant(player.getUUID());
        if (table.take(questId, player.getUUID(), player.getGameProfile().getName())) {
            player.sendSystemMessage(Component.translatable("message.townquest.book_taken", quest.getTitle()));
        } else if (already) {
            player.sendSystemMessage(Component.translatable("message.townquest.book_already_joined"));
        } else {
            player.sendSystemMessage(Component.translatable("message.townquest.book_already_taken"));
        }
    }

    public static void release(ServerPlayer player, BoundContainer key, UUID questId) {
        QuestTableBlockEntity table = loadedTable(player, key);
        if (table == null) return;

        Quest quest = table.findQuest(questId);
        if (quest == null) return;

        if (table.release(questId, player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.townquest.book_released", quest.getTitle()));
        }
    }

    /** Виконавець ручного квесту відправляє роботу на перевірку. */
    public static void submit(ServerPlayer player, BoundContainer key, UUID questId) {
        QuestTableBlockEntity table = loadedTable(player, key);
        if (table == null) return;

        Quest quest = table.findQuest(questId);
        if (quest == null || !isVisibleTo(quest, player.getUUID())) return;

        if (table.submit(questId, player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.townquest.submitted", quest.getTitle()));
            table.notifyManagers(player.server, Component.translatable("message.townquest.review_request",
                    player.getGameProfile().getName(), quest.getTitle()));
        }
    }

    /** Повертає стіл лише якщо його чанк завантажений. Інакше пише гравцю підказку. */
    private static QuestTableBlockEntity loadedTable(ServerPlayer player, BoundContainer key) {
        ServerLevel level = player.server.getLevel(key.dimension());
        if (level == null || !level.isLoaded(key.pos())) {
            player.sendSystemMessage(Component.translatable("message.townquest.book_table_unloaded"));
            return null;
        }
        if (level.getBlockEntity(key.pos()) instanceof QuestTableBlockEntity table) {
            return table;
        }
        player.sendSystemMessage(Component.translatable("message.townquest.book_table_unloaded"));
        return null;
    }
}
