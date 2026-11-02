package org.example.yamirkuro.townquest.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.example.yamirkuro.townquest.quest.Quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Знімок квестів усіх столів + контекст (власник, сундуки).
 * Quest Book бачить квести навіть коли чанк зі столом не завантажений.
 */
public class QuestRegistry extends SavedData {

    private static final String NAME = "townquest_registry";

    private final Map<BoundContainer, TableSnapshot> tables = new HashMap<>();

    public static QuestRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(QuestRegistry::load, QuestRegistry::new, NAME);
    }

    public void update(BoundContainer key, List<Quest> quests, String ownerName, List<BlockPos> chests) {
        List<Quest> copy = new ArrayList<>();
        for (Quest quest : quests) {
            copy.add(quest.copy());
        }
        tables.put(key, new TableSnapshot(copy, ownerName, chests));
        setDirty();
    }

    /** Сумісність зі старим викликом update(key, quests). */
    public void update(BoundContainer key, List<Quest> quests) {
        TableSnapshot prev = tables.get(key);
        String owner = prev != null ? prev.ownerName() : "";
        List<BlockPos> chests = prev != null ? prev.chestPositions() : List.of();
        update(key, quests, owner, chests);
    }

    public void remove(BoundContainer key) {
        if (tables.remove(key) != null) {
            setDirty();
        }
    }

    public Map<BoundContainer, TableSnapshot> allSnapshots() {
        return Collections.unmodifiableMap(tables);
    }

    public Map<BoundContainer, List<Quest>> all() {
        Map<BoundContainer, List<Quest>> out = new HashMap<>();
        for (Map.Entry<BoundContainer, TableSnapshot> e : tables.entrySet()) {
            out.put(e.getKey(), e.getValue().questsCopy());
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<BoundContainer, TableSnapshot> entry : tables.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.put("Table", entry.getKey().save());
            TableSnapshot snap = entry.getValue();
            ListTag quests = new ListTag();
            for (Quest quest : snap.quests()) {
                quests.add(quest.save());
            }
            t.put("Quests", quests);
            t.putString("OwnerName", snap.ownerName());
            ListTag chests = new ListTag();
            for (BlockPos pos : snap.chestPositions()) {
                CompoundTag p = new CompoundTag();
                p.putLong("Pos", pos.asLong());
                chests.add(p);
            }
            t.put("Chests", chests);
            list.add(t);
        }
        tag.put("Tables", list);
        return tag;
    }

    public static QuestRegistry load(CompoundTag tag) {
        QuestRegistry registry = new QuestRegistry();
        for (Tag raw : tag.getList("Tables", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            BoundContainer key = BoundContainer.load(t.getCompound("Table"));
            List<Quest> quests = new ArrayList<>();
            for (Tag q : t.getList("Quests", Tag.TAG_COMPOUND)) {
                quests.add(Quest.load((CompoundTag) q));
            }
            String ownerName = t.getString("OwnerName");
            List<BlockPos> chests = new ArrayList<>();
            for (Tag c : t.getList("Chests", Tag.TAG_COMPOUND)) {
                chests.add(BlockPos.of(((CompoundTag) c).getLong("Pos")));
            }
            registry.tables.put(key, new TableSnapshot(quests, ownerName, chests));
        }
        return registry;
    }
}
