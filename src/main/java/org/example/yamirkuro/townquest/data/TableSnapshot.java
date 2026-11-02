package org.example.yamirkuro.townquest.data;

import net.minecraft.core.BlockPos;
import org.example.yamirkuro.townquest.quest.Quest;

import java.util.ArrayList;
import java.util.List;

/**
 * Знімок столу в QuestRegistry: квести + хто видав + сундуки здачі.
 * Потрібен, щоб книга показувала контекст навіть без завантаженого BlockEntity.
 */
public final class TableSnapshot {

    private final List<Quest> quests;
    private final String ownerName;
    private final List<BlockPos> chestPositions;

    public TableSnapshot(List<Quest> quests, String ownerName, List<BlockPos> chestPositions) {
        this.quests = List.copyOf(quests);
        this.ownerName = ownerName == null ? "" : ownerName;
        this.chestPositions = List.copyOf(chestPositions == null ? List.of() : chestPositions);
    }

    public List<Quest> quests() {
        return quests;
    }

    public String ownerName() {
        return ownerName;
    }

    public List<BlockPos> chestPositions() {
        return chestPositions;
    }

    /** Зручна копія квестів (мутабельна), якщо хтось очікує ArrayList. */
    public List<Quest> questsCopy() {
        return new ArrayList<>(quests);
    }
}
