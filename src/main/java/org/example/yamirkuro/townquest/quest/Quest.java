package org.example.yamirkuro.townquest.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Quest {

    public static final int MAX_TITLE = 40;
    public static final int MAX_DESC = 120;
    public static final int MAX_AMOUNT = 100_000;

    private final UUID id;
    private final String title;
    private final String description;
    private final ResourceLocation item;
    private final int required;
    private final QuestType type;
    @Nullable
    private final UUID assignee;
    private final String assigneeName;
    /** Учасники GROUP-квесту. */
    private final List<UUID> groupMembers;
    private final List<String> groupMemberNames;
    private final QuestKind kind;
    @Nullable
    private final QuestLocation location;
    /** Для ITEMS: true = після здачі потрібна перевірка власником (REVIEW). */
    private final boolean requireReview;

    private QuestStatus status = QuestStatus.OPEN;
    private int delivered;
    @Nullable
    private UUID takenBy;
    private String takenByName = "";
    private long takenAt;
    private int inInventory;
    /** Учасники для GLOBAL/GROUP (мульти). PERSONAL лишає takenBy. */
    private final java.util.List<QuestParticipant> participants = new java.util.ArrayList<>();

    public Quest(UUID id, String title, String description, ResourceLocation item, int required,
                 QuestType type, @Nullable UUID assignee, String assigneeName,
                 List<UUID> groupMembers, List<String> groupMemberNames,
                 QuestKind kind, @Nullable QuestLocation location, boolean requireReview) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.item = item;
        this.required = required;
        this.type = type;
        this.assignee = assignee;
        this.assigneeName = assigneeName == null ? "" : assigneeName;
        this.groupMembers = List.copyOf(groupMembers == null ? List.of() : groupMembers);
        this.groupMemberNames = List.copyOf(groupMemberNames == null ? List.of() : groupMemberNames);
        this.kind = kind;
        this.location = location;
        this.requireReview = requireReview;
    }

    public static Quest create(String title, String description, ResourceLocation item, int required,
                               QuestType type, @Nullable UUID assignee, String assigneeName,
                               List<UUID> groupMembers, List<String> groupMemberNames,
                               QuestKind kind, @Nullable QuestLocation location, boolean requireReview) {
        return new Quest(UUID.randomUUID(), title, description, item, required,
                type, assignee, assigneeName, groupMembers, groupMemberNames, kind, location, requireReview);
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ResourceLocation getItem() { return item; }
    public int getRequired() { return required; }
    public QuestType getType() { return type; }
    @Nullable public UUID getAssignee() { return assignee; }
    public String getAssigneeName() { return assigneeName; }
    public List<UUID> getGroupMembers() { return groupMembers; }
    public List<String> getGroupMemberNames() { return groupMemberNames; }
    public boolean isGroupMember(UUID player) { return groupMembers.contains(player); }
    public QuestKind getKind() { return kind; }
    @Nullable public QuestLocation getLocation() { return location; }
    public boolean isRequireReview() { return requireReview; }
    public QuestStatus getStatus() { return status; }
    public int getDelivered() { return delivered; }
    @Nullable public UUID getTakenBy() { return takenBy; }
    public String getTakenByName() { return takenByName; }
    public long getTakenAt() { return takenAt; }
    public int getInInventory() { return inInventory; }
    public boolean isTaken() { return takenBy != null || !participants.isEmpty(); }

    public boolean allowsMultiple() {
        return type == QuestType.GLOBAL || type == QuestType.GROUP;
    }

    public java.util.List<QuestParticipant> getParticipants() {
        return java.util.Collections.unmodifiableList(participants);
    }

    public boolean hasParticipant(java.util.UUID player) {
        // для мульти дивимось лише список participants (takenBy — відображення)
        if (allowsMultiple()) {
            for (QuestParticipant p : participants) {
                if (player.equals(p.getId())) return true;
            }
            return false;
        }
        return player.equals(takenBy);
    }

    public String participantNamesJoined() {
        if (participants.isEmpty()) {
            return takenByName == null ? "" : takenByName;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < participants.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(participants.get(i).getName());
        }
        return sb.toString();
    }

    public QuestParticipant getParticipant(java.util.UUID player) {
        for (QuestParticipant p : participants) {
            if (player.equals(p.getId())) return p;
        }
        return null;
    }

    /** Статус саме цього гравця (для кнопок у книзі). */
    public QuestStatus statusFor(java.util.UUID player) {
        if (!allowsMultiple()) {
            if (player.equals(takenBy)) return status;
            return status == QuestStatus.OPEN ? QuestStatus.OPEN : status;
        }
        QuestParticipant p = getParticipant(player);
        if (p != null) return p.getStatus();
        if (status == QuestStatus.DONE) return QuestStatus.DONE;
        return QuestStatus.OPEN; // ще не взяв
    }

    public int percent() {
        if (kind == QuestKind.MANUAL) {
            return status == QuestStatus.DONE ? 100 : 0;
        }
        if (required <= 0) return 0;
        return (int) Math.min(100L, (long) delivered * 100L / required);
    }

    /** Повертає true, якщо значення змінилось. */
    public boolean setStatus(QuestStatus value) {
        if (value == status) return false;
        status = value;
        return true;
    }

    public boolean setDelivered(int value) {
        if (value == delivered) return false;
        delivered = value;
        return true;
    }

    public boolean setInInventory(int value) {
        if (value == inInventory) return false;
        inInventory = value;
        return true;
    }

    public void take(UUID player, String playerName, long timeMillis) {
        if (allowsMultiple()) {
            if (hasParticipant(player)) return;
            participants.add(new QuestParticipant(player, playerName, QuestStatus.IN_PROGRESS, timeMillis));
            if (takenBy == null) {
                this.takenBy = player;
                this.takenByName = playerName;
                this.takenAt = timeMillis;
            }
            if (status == QuestStatus.OPEN) {
                this.status = QuestStatus.IN_PROGRESS;
            }
            // REVIEW/IN_PROGRESS: просто додаємо учасника, статус квесту не гіршимо
            return;
        }
        this.takenBy = player;
        this.takenByName = playerName;
        this.takenAt = timeMillis;
        this.status = QuestStatus.IN_PROGRESS;
    }

    /** Повна відмова (PERSONAL / старий шлях). */
    public void release() {
        this.takenBy = null;
        this.takenByName = "";
        this.takenAt = 0L;
        this.inInventory = 0;
        this.participants.clear();
        this.status = QuestStatus.OPEN;
    }

    /** Відмова одного учасника в мульти-квесті. */
    public boolean releaseParticipant(UUID player) {
        if (!allowsMultiple()) {
            if (player.equals(takenBy)) {
                release();
                return true;
            }
            return false;
        }
        boolean removed = participants.removeIf(p -> player.equals(p.getId()));
        if (!removed) return false;
        if (player.equals(takenBy)) {
            if (participants.isEmpty()) {
                takenBy = null;
                takenByName = "";
                takenAt = 0L;
            } else {
                QuestParticipant first = participants.get(0);
                takenBy = first.getId();
                takenByName = first.getName();
                takenAt = first.getTakenAt();
            }
        }
        if (participants.isEmpty() && status != QuestStatus.DONE) {
            status = QuestStatus.OPEN;
        }
        return true;
    }

    public boolean submitParticipant(UUID player) {
        if (allowsMultiple()) {
            QuestParticipant p = getParticipant(player);
            if (p == null || p.getStatus() != QuestStatus.IN_PROGRESS) return false;
            p.setStatus(QuestStatus.REVIEW);
            recomputeAggregateStatus(); // квест = REVIEW лише якщо хтось на перевірці; інші лишаються в роботі
            return true;
        }
        if (!player.equals(takenBy) || status != QuestStatus.IN_PROGRESS) return false;
        status = QuestStatus.REVIEW;
        return true;
    }

    public int countInReview() {
        if (allowsMultiple()) {
            int n = 0;
            for (QuestParticipant p : participants) {
                if (p.getStatus() == QuestStatus.REVIEW) n++;
            }
            return n;
        }
        return status == QuestStatus.REVIEW ? 1 : 0;
    }

    public boolean hasAnyoneInReview() {
        return countInReview() > 0;
    }

    public boolean reviewParticipant(UUID player, boolean approve) {
        if (allowsMultiple()) {
            QuestParticipant p = getParticipant(player);
            if (p == null || p.getStatus() != QuestStatus.REVIEW) return false;
            p.setStatus(approve ? QuestStatus.DONE : QuestStatus.IN_PROGRESS);
            recomputeAggregateStatus();
            return true;
        }
        if (status != QuestStatus.REVIEW) return false;
        status = approve ? QuestStatus.DONE : QuestStatus.IN_PROGRESS;
        return true;
    }

    private void recomputeAggregateStatus() {
        if (participants.isEmpty()) {
            if (status != QuestStatus.DONE) status = QuestStatus.OPEN;
            return;
        }
        boolean anyReview = false;
        boolean anyActive = false; // ще не DONE
        boolean allDone = true;
        for (QuestParticipant p : participants) {
            if (p.getStatus() == QuestStatus.REVIEW) anyReview = true;
            if (p.getStatus() != QuestStatus.DONE) {
                allDone = false;
                anyActive = true;
            }
        }
        // ручний мульти: DONE лише коли ВСІ хто взяв — виконали
        if (allDone) status = QuestStatus.DONE;
        else if (anyReview) status = QuestStatus.REVIEW;
        else if (anyActive) status = QuestStatus.IN_PROGRESS;
        else status = QuestStatus.OPEN;
    }

    /** Власник примусово завершує квест (усі учасники → DONE). */
    public void forceComplete() {
        if (allowsMultiple()) {
            for (QuestParticipant p : participants) {
                p.setStatus(QuestStatus.DONE);
            }
        }
        status = QuestStatus.DONE;
    }

    public Quest copy() {
        return load(save());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Title", title);
        tag.putString("Description", description);
        tag.putString("Item", item.toString());
        tag.putInt("Required", required);
        tag.putInt("Delivered", delivered);
        tag.putString("Type", type.name());
        tag.putString("Kind", kind.name());
        tag.putString("Status", status.name());
        if (assignee != null) {
            tag.putUUID("Assignee", assignee);
            tag.putString("AssigneeName", assigneeName);
        }
        ListTag group = new ListTag();
        for (int i = 0; i < groupMembers.size(); i++) {
            CompoundTag m = new CompoundTag();
            m.putUUID("Id", groupMembers.get(i));
            if (i < groupMemberNames.size()) {
                m.putString("Name", groupMemberNames.get(i));
            }
            group.add(m);
        }
        tag.put("Group", group);
        if (takenBy != null) {
            tag.putUUID("TakenBy", takenBy);
            tag.putString("TakenByName", takenByName);
            tag.putLong("TakenAt", takenAt);
        }
        if (location != null) {
            tag.put("Loc", location.save());
        }
        tag.putBoolean("RequireReview", requireReview);
        tag.putInt("InInv", inInventory);
        ListTag parts = new ListTag();
        for (QuestParticipant p : participants) {
            parts.add(p.save());
        }
        tag.put("Participants", parts);
        return tag;
    }

    public static Quest load(CompoundTag tag) {
        ResourceLocation item = ResourceLocation.tryParse(tag.getString("Item"));
        if (item == null) {
            item = new ResourceLocation("minecraft:air");
        }
        UUID assignee = tag.hasUUID("Assignee") ? tag.getUUID("Assignee") : null;
        QuestLocation location = tag.contains("Loc", Tag.TAG_COMPOUND)
                ? QuestLocation.load(tag.getCompound("Loc")) : null;

        List<UUID> groupMembers = new ArrayList<>();
        List<String> groupNames = new ArrayList<>();
        for (Tag t : tag.getList("Group", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) t;
            if (m.hasUUID("Id")) {
                groupMembers.add(m.getUUID("Id"));
                groupNames.add(m.getString("Name"));
            }
        }

        Quest quest = new Quest(
                tag.getUUID("Id"),
                tag.getString("Title"),
                tag.getString("Description"),
                item,
                tag.getInt("Required"),
                QuestType.byName(tag.getString("Type")),
                assignee,
                tag.getString("AssigneeName"),
                groupMembers,
                groupNames,
                QuestKind.byName(tag.getString("Kind")),
                location,
                tag.getBoolean("RequireReview"));

        quest.delivered = tag.getInt("Delivered");
        if (tag.hasUUID("TakenBy")) {
            quest.takenBy = tag.getUUID("TakenBy");
            quest.takenByName = tag.getString("TakenByName");
            quest.takenAt = tag.getLong("TakenAt");
        }
        quest.inInventory = tag.getInt("InInv");

        // квести зі старих збережень не мали статусу: виводимо його з того, чи взято квест
        if (tag.contains("Status")) {
            quest.status = QuestStatus.byName(tag.getString("Status"));
        } else {
            quest.status = quest.takenBy != null ? QuestStatus.IN_PROGRESS : QuestStatus.OPEN;
        }

        // учасники GLOBAL/GROUP
        if (tag.contains("Participants", Tag.TAG_LIST)) {
            for (Tag t : tag.getList("Participants", Tag.TAG_COMPOUND)) {
                quest.participants.add(QuestParticipant.load((CompoundTag) t));
            }
        }
        // міграція: старий takenBy без Participants
        if (quest.allowsMultiple() && quest.participants.isEmpty() && quest.takenBy != null) {
            quest.participants.add(new QuestParticipant(
                    quest.takenBy, quest.takenByName, quest.status, quest.takenAt));
        }
        return quest;
    }
}
