package org.example.yamirkuro.townquest.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.example.yamirkuro.townquest.data.BoundContainer;
import org.example.yamirkuro.townquest.data.QuestRegistry;
import org.example.yamirkuro.townquest.menu.QuestTableMenu;
import org.example.yamirkuro.townquest.item.QuestArchiveItem;
import org.example.yamirkuro.townquest.quest.Quest;
import org.example.yamirkuro.townquest.registry.ModItems;
import org.example.yamirkuro.townquest.quest.QuestKind;
import org.example.yamirkuro.townquest.quest.QuestStatus;
import org.example.yamirkuro.townquest.registry.ModBlockEntities;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class QuestTableBlockEntity extends BlockEntity implements MenuProvider {

    public static final int MAX_QUESTS = 50;
    public static final int MAX_CONTAINERS = 8;

    public enum BindResult { ADDED, REMOVED, LIMIT_REACHED }

    @Nullable
    private UUID owner;
    private final Set<UUID> trusted = new HashSet<>();
    private final List<Quest> quests = new ArrayList<>();
    private final List<BoundContainer> containers = new ArrayList<>();
    /** Чи вставлена головна книга. Без неї стіл — порожній термінал. */
    private boolean archivePresent;

    public QuestTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUEST_TABLE.get(), pos, state);
    }

    // ---------- власник і права ----------

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        markDirtyAndSync();
    }

    public boolean addTrusted(UUID id) {
        boolean added = trusted.add(id);
        if (added) markDirtyAndSync();
        return added;
    }

    public boolean removeTrusted(UUID id) {
        boolean removed = trusted.remove(id);
        if (removed) markDirtyAndSync();
        return removed;
    }

    public boolean canManage(UUID id) {
        return id.equals(owner) || trusted.contains(id);
    }

    public boolean hasArchive() {
        return archivePresent;
    }

    /** Вставити книгу: дані з предмета → стіл. Порожня книга дає порожній стіл. */
    public boolean insertArchive(ItemStack stack, UUID inserter) {
        if (archivePresent) return false;
        if (!stack.is(ModItems.QUEST_ARCHIVE.get())) return false;

        CompoundTag archive = QuestArchiveItem.getArchiveTag(stack);
        quests.clear();
        containers.clear();
        trusted.clear();

        if (archive != null) {
            if (archive.hasUUID("Owner")) {
                owner = archive.getUUID("Owner");
            } else if (owner == null) {
                owner = inserter;
            }
            for (Tag t : archive.getList("Trusted", Tag.TAG_INT_ARRAY)) {
                trusted.add(NbtUtils.loadUUID(t));
            }
            for (Tag t : archive.getList("Quests", Tag.TAG_COMPOUND)) {
                quests.add(Quest.load((CompoundTag) t));
            }
            for (Tag t : archive.getList("Containers", Tag.TAG_COMPOUND)) {
                containers.add(BoundContainer.load((CompoundTag) t));
            }
        } else if (owner == null) {
            owner = inserter;
        }

        archivePresent = true;
        stack.shrink(1);
        markDirtyAndSync();
        return true;
    }

    /** Вийняти книгу з даними столу. Стіл стає порожнім. */
    public ItemStack extractArchive() {
        if (!archivePresent) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(ModItems.QUEST_ARCHIVE.get());
        QuestArchiveItem.applyToStack(stack, owner, trusted, quests, containers);

        quests.clear();
        containers.clear();
        trusted.clear();
        // owner лишаємо на столі-оболонці для прав insert
        archivePresent = false;
        markDirtyAndSync();
        if (level != null && level.getServer() != null) {
            QuestRegistry.get(level.getServer())
                    .remove(new BoundContainer(level.dimension(), worldPosition.immutable()));
        }
        return stack;
    }

    /** Знімок для дропу при поломці. */
    public ItemStack createArchiveDrop() {
        if (!archivePresent) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(ModItems.QUEST_ARCHIVE.get());
        QuestArchiveItem.applyToStack(stack, owner, trusted, quests, containers);
        return stack;
    }


    /** Надсилає повідомлення всім, хто керує столом і зараз у мережі. */
    public void notifyManagers(MinecraftServer server, Component message) {
        Set<UUID> targets = new HashSet<>(trusted);
        if (owner != null) targets.add(owner);
        for (UUID id : targets) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    // ---------- квести ----------

    public List<Quest> getQuests() {
        return Collections.unmodifiableList(quests);
    }

    @Nullable
    public Quest findQuest(UUID questId) {
        for (Quest quest : quests) {
            if (quest.getId().equals(questId)) return quest;
        }
        return null;
    }

    public boolean addQuest(Quest quest) {
        if (quests.size() >= MAX_QUESTS) return false;
        quests.add(quest);
        markDirtyAndSync();
        return true;
    }

    public boolean removeQuest(UUID questId) {
        boolean removed = quests.removeIf(q -> q.getId().equals(questId));
        if (removed) markDirtyAndSync();
        return removed;
    }

    /** Гравець бере квест. Можливо лише для відкритого квесту. */
    public boolean take(UUID questId, UUID player, String playerName) {
        Quest quest = findQuest(questId);
        if (quest == null) return false;
        if (quest.getType() == org.example.yamirkuro.townquest.quest.QuestType.GROUP
                && !quest.isGroupMember(player)) {
            return false;
        }
        if (quest.getType() == org.example.yamirkuro.townquest.quest.QuestType.PERSONAL
                && quest.getAssignee() != null && !player.equals(quest.getAssignee())) {
            return false;
        }
        if (quest.allowsMultiple()) {
            // GLOBAL/GROUP: приєднатись може будь-хто, поки квест не DONE
            if (quest.getStatus() == QuestStatus.DONE) return false;
            if (quest.hasParticipant(player)) return false;
            // міграція: старий takenBy без participants
            if (quest.getTakenBy() != null && quest.getParticipants().isEmpty()) {
                quest.take(quest.getTakenBy(), quest.getTakenByName(), quest.getTakenAt());
            }
            quest.take(player, playerName, System.currentTimeMillis());
            markDirtyAndSync();
            return true;
        }
        if (quest.getStatus() != QuestStatus.OPEN) return false;
        quest.take(player, playerName, System.currentTimeMillis());
        markDirtyAndSync();
        return true;
    }

    /** Гравець відмовляється від свого квесту. */
    public boolean release(UUID questId, UUID player) {
        Quest quest = findQuest(questId);
        if (quest == null) return false;
        if (quest.allowsMultiple()) {
            if (!quest.releaseParticipant(player)) return false;
            markDirtyAndSync();
            return true;
        }
        if (quest.getStatus() != QuestStatus.IN_PROGRESS || !player.equals(quest.getTakenBy())) return false;
        quest.release();
        markDirtyAndSync();
        return true;
    }

    /** Виконавець ручного квесту натискає «Завершити». */
    public boolean submit(UUID questId, UUID player) {
        Quest quest = findQuest(questId);
        if (quest == null || quest.getKind() != QuestKind.MANUAL) return false;
        if (!quest.submitParticipant(player)) return false;
        markDirtyAndSync();
        return true;
    }

    /** Власник підтверджує / відхиляє (для PERSONAL — весь квест; для мульти — participantId або null = усі REVIEW). */
    public boolean review(UUID questId, boolean approve) {
        return review(questId, approve, null);
    }

    /** Власник примусово ставить квест у DONE (навіть якщо хтось ще в роботі). */
    public boolean forceComplete(UUID questId) {
        Quest quest = findQuest(questId);
        if (quest == null || quest.getStatus() == QuestStatus.DONE) return false;
        quest.forceComplete();
        markDirtyAndSync();
        return true;
    }

    public boolean review(UUID questId, boolean approve, UUID participantId) {
        Quest quest = findQuest(questId);
        if (quest == null) return false;
        boolean okKind = quest.getKind() == QuestKind.MANUAL
                || (quest.getKind() == QuestKind.ITEMS && quest.isRequireReview());
        if (!okKind) return false;

        if (quest.allowsMultiple()) {
            if (participantId != null) {
                if (!quest.reviewParticipant(participantId, approve)) return false;
            } else {
                // усіх хто в REVIEW
                boolean any = false;
                for (var p : new java.util.ArrayList<>(quest.getParticipants())) {
                    if (p.getStatus() == QuestStatus.REVIEW) {
                        if (quest.reviewParticipant(p.getId(), approve)) any = true;
                    }
                }
                if (!any && quest.getStatus() == QuestStatus.REVIEW) {
                    quest.setStatus(approve ? QuestStatus.DONE : QuestStatus.IN_PROGRESS);
                } else if (!any) return false;
            }
            markDirtyAndSync();
            return true;
        }

        if (quest.getStatus() != QuestStatus.REVIEW) return false;
        quest.setStatus(approve ? QuestStatus.DONE : QuestStatus.IN_PROGRESS);
        markDirtyAndSync();
        return true;
    }

    // ---------- прив'язані контейнери ----------

    public List<BoundContainer> getContainers() {
        return Collections.unmodifiableList(containers);
    }

    /** Якщо контейнер уже прив'язаний, відв'язує його; інакше прив'язує. */
    public BindResult toggleContainer(BoundContainer container) {
        if (containers.remove(container)) {
            markDirtyAndSync();
            return BindResult.REMOVED;
        }
        if (containers.size() >= MAX_CONTAINERS) {
            return BindResult.LIMIT_REACHED;
        }
        containers.add(container);
        markDirtyAndSync();
        return BindResult.ADDED;
    }

    // ---------- підрахунок прогресу (сервер) ----------

    public static void serverTick(Level level, BlockPos pos, BlockState state, QuestTableBlockEntity table) {
        if (level.isClientSide) return;
        if (table.quests.isEmpty()) return;
        // раз на секунду, зі зсувом за координатами, щоб столи не рахували в один тік
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 20L) != 0) return;
        table.recount(level);
    }

    private void recount(Level level) {
        MinecraftServer server = level.getServer();
        if (server == null) return;

        boolean changed = updateInventoryCounts(server);
        if (updateDeliveredFromContainers(server)) {
            changed = true;
        }
        if (changed) {
            markDirtyAndSync();
        }
    }

    /** Скільки потрібних предметів зараз в інвентарі виконавця (якщо він у мережі). */
    private boolean updateInventoryCounts(MinecraftServer server) {
        boolean changed = false;
        for (Quest quest : quests) {
            if (quest.getKind() != QuestKind.ITEMS || quest.getStatus() == QuestStatus.DONE) continue;
            UUID taker = quest.getTakenBy();
            if (taker == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(taker);
            if (player == null) continue;

            Item item = ForgeRegistries.ITEMS.getValue(quest.getItem());
            int count = 0;
            if (item != null) {
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.is(item)) count += stack.getCount();
                }
                for (ItemStack stack : player.getInventory().offhand) {
                    if (stack.is(item)) count += stack.getCount();
                }
            }
            if (quest.setInInventory(count)) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Рахує предмети в усіх прив'язаних контейнерах і розподіляє їх між квестами по черзі,
     * тому два квести на один предмет не рахують одні й ті самі предмети двічі.
     * Виконаний квест (DONE) більше не змінюється, але й далі «резервує» свої предмети.
     * Якщо хоч один контейнер у незавантаженому чанку, нічого не змінюємо.
     */
    private boolean updateDeliveredFromContainers(MinecraftServer server) {
        Map<Item, Integer> pool = new HashMap<>();
        List<BoundContainer> broken = new ArrayList<>();

        for (BoundContainer c : containers) {
            ServerLevel containerLevel = server.getLevel(c.dimension());
            if (containerLevel == null) {
                broken.add(c);
                continue;
            }
            if (!containerLevel.isLoaded(c.pos())) {
                return false;
            }
            BlockEntity be = containerLevel.getBlockEntity(c.pos());
            LazyOptional<IItemHandler> cap = be == null
                    ? LazyOptional.empty()
                    : be.getCapability(ForgeCapabilities.ITEM_HANDLER);
            if (!cap.isPresent()) {
                broken.add(c);
                continue;
            }
            cap.ifPresent(handler -> {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (!stack.isEmpty()) {
                        pool.merge(stack.getItem(), stack.getCount(), Integer::sum);
                    }
                }
            });
        }

        boolean changed = !broken.isEmpty() && containers.removeAll(broken);

        for (Quest quest : quests) {
            if (quest.getKind() != QuestKind.ITEMS) continue;

            Item item = ForgeRegistries.ITEMS.getValue(quest.getItem());
            if (item == null) continue;
            int available = pool.getOrDefault(item, 0);

            if (quest.getStatus() == QuestStatus.DONE) {
                pool.put(item, available - Math.min(quest.getRequired(), available));
                continue;
            }

            int assigned = Math.min(quest.getRequired(), available);
            pool.put(item, available - assigned);
            if (quest.setDelivered(assigned)) {
                changed = true;
            }

            QuestStatus st = quest.getStatus();
            if (quest.getRequired() > 0 && assigned >= quest.getRequired()) {
                if (st == QuestStatus.IN_PROGRESS || (quest.allowsMultiple() && !quest.getParticipants().isEmpty()
                        && st != QuestStatus.DONE && st != QuestStatus.REVIEW)) {
                    if (quest.isRequireReview()) {
                        if (quest.setStatus(QuestStatus.REVIEW)) {
                            changed = true;
                            for (var p : quest.getParticipants()) {
                                if (p.getStatus() == QuestStatus.IN_PROGRESS) {
                                    p.setStatus(QuestStatus.REVIEW);
                                }
                            }
                            notifyItemReview(server, quest, item);
                        }
                    } else {
                        if (quest.setStatus(QuestStatus.DONE)) {
                            changed = true;
                            for (var p : quest.getParticipants()) {
                                p.setStatus(QuestStatus.DONE);
                            }
                        }
                    }
                }
            } else if (st == QuestStatus.REVIEW && assigned < quest.getRequired()) {
                // забрали предмети з сундука під час перевірки
                if (quest.setStatus(QuestStatus.IN_PROGRESS)) {
                    changed = true;
                    notifyManagers(server, Component.translatable(
                            "message.townquest.review_items_removed",
                            quest.getTakenByName().isEmpty() ? "?" : quest.getTakenByName(),
                            quest.getTitle()));
                }
            }
        }
        return changed;
    }

    /** Повідомляє адмінів столу: хто здав, який квест, у яких сундуках є предмет. */
    private void notifyItemReview(MinecraftServer server, Quest quest, Item item) {
        StringBuilder chests = new StringBuilder();
        for (BoundContainer c : containers) {
            ServerLevel cl = server.getLevel(c.dimension());
            if (cl == null || !cl.isLoaded(c.pos())) continue;
            BlockEntity be = cl.getBlockEntity(c.pos());
            if (be == null) continue;
            LazyOptional<IItemHandler> cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER);
            boolean has = cap.map(handler -> {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (!stack.isEmpty() && stack.is(item)) return true;
                }
                return false;
            }).orElse(false);
            if (has) {
                if (chests.length() > 0) chests.append(", ");
                BlockPos p = c.pos();
                chests.append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ());
            }
        }
        String chestStr = chests.length() == 0 ? "-" : chests.toString();
        String who = quest.getTakenByName().isEmpty() ? "?" : quest.getTakenByName();
        notifyManagers(server, Component.translatable(
                "message.townquest.review_items_ready",
                who, quest.getTitle(), chestStr));
    }

    // ---------- збереження і синхронізація ----------

    private void markDirtyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            pushToRegistry();
        }
    }

    /** Координати прив'язаних сундуків для книги / реєстру. */
    public List<BlockPos> getContainerPositions() {
        List<BlockPos> list = new ArrayList<>(containers.size());
        for (BoundContainer c : containers) {
            list.add(c.pos());
        }
        return list;
    }

    /** Ім'я власника (онлайн або з кешу профілів). */
    public String resolveOwnerName() {
        if (owner == null || level == null || level.getServer() == null) return "";
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(owner);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return level.getServer().getProfileCache()
                .get(owner)
                .map(com.mojang.authlib.GameProfile::getName)
                .orElse("");
    }

    /** Оновлює знімок цього столу в QuestRegistry (для Quest Book). */
    private void pushToRegistry() {
        if (level == null || level.isClientSide || level.getServer() == null) return;
        BoundContainer key = new BoundContainer(level.dimension(), worldPosition.immutable());
        if (!archivePresent) {
            QuestRegistry.get(level.getServer()).remove(key);
            return;
        }
        QuestRegistry.get(level.getServer()).update(
                key,
                quests,
                resolveOwnerName(),
                getContainerPositions());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        pushToRegistry();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        ListTag trustedList = new ListTag();
        for (UUID id : trusted) {
            trustedList.add(NbtUtils.createUUID(id));
        }
        tag.put("Trusted", trustedList);

        ListTag questList = new ListTag();
        for (Quest quest : quests) {
            questList.add(quest.save());
        }
        tag.put("Quests", questList);

        ListTag containerList = new ListTag();
        for (BoundContainer c : containers) {
            containerList.add(c.save());
        }
        tag.put("Containers", containerList);
        tag.putBoolean("ArchivePresent", archivePresent);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;

        trusted.clear();
        for (Tag t : tag.getList("Trusted", Tag.TAG_INT_ARRAY)) {
            trusted.add(NbtUtils.loadUUID(t));
        }

        quests.clear();
        for (Tag t : tag.getList("Quests", Tag.TAG_COMPOUND)) {
            quests.add(Quest.load((CompoundTag) t));
        }

        containers.clear();
        for (Tag t : tag.getList("Containers", Tag.TAG_COMPOUND)) {
            containers.add(BoundContainer.load((CompoundTag) t));
        }

        if (tag.contains("ArchivePresent")) {
            archivePresent = tag.getBoolean("ArchivePresent");
        } else {
            // старі світи: якщо вже є квести — вважаємо книгу вставленою
            archivePresent = !quests.isEmpty() || !containers.isEmpty();
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---------- GUI ----------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.townquest.quest_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new QuestTableMenu(containerId, inventory, worldPosition);
    }
}
