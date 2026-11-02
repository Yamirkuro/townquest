package org.example.yamirkuro.townquest.quest;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Учасник мульти-квесту (GLOBAL / GROUP): особистий статус. */
public final class QuestParticipant {

    private final UUID id;
    private final String name;
    private QuestStatus status;
    private final long takenAt;

    public QuestParticipant(UUID id, String name, QuestStatus status, long takenAt) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.status = status == null ? QuestStatus.IN_PROGRESS : status;
        this.takenAt = takenAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public QuestStatus getStatus() { return status; }
    public long getTakenAt() { return takenAt; }

    public void setStatus(QuestStatus status) {
        this.status = status;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putString("Status", status.name());
        tag.putLong("TakenAt", takenAt);
        return tag;
    }

    public static QuestParticipant load(CompoundTag tag) {
        return new QuestParticipant(
                tag.getUUID("Id"),
                tag.getString("Name"),
                QuestStatus.byName(tag.getString("Status")),
                tag.getLong("TakenAt"));
    }
}
