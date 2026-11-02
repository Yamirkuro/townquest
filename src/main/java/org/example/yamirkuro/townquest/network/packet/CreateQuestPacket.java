package org.example.yamirkuro.townquest.network.packet;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.example.yamirkuro.townquest.block.entity.QuestTableBlockEntity;
import org.example.yamirkuro.townquest.quest.Quest;
import org.example.yamirkuro.townquest.quest.QuestKind;
import org.example.yamirkuro.townquest.quest.QuestLocation;
import org.example.yamirkuro.townquest.quest.QuestType;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/** Клієнт -> сервер: створити квест у столі. Усе перевіряється на сервері. */
public class CreateQuestPacket {

    private final BlockPos pos;
    private final String title;
    private final String description;
    private final String itemId;
    private final int amount;
    private final QuestType type;
    private final String assigneeName;
    private final QuestKind kind;
    private final boolean requireReview;
    @Nullable
    private final QuestLocation location;

    public CreateQuestPacket(BlockPos pos, String title, String description, String itemId, int amount,
                             QuestType type, String assigneeName, QuestKind kind, boolean requireReview,
                             @Nullable QuestLocation location) {
        this.pos = pos;
        this.title = title;
        this.description = description;
        this.itemId = itemId;
        this.amount = amount;
        this.type = type;
        this.assigneeName = assigneeName;
        this.kind = kind;
        this.requireReview = requireReview;
        this.location = location;
    }

    public static void encode(CreateQuestPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.title, Quest.MAX_TITLE);
        buf.writeUtf(msg.description, Quest.MAX_DESC);
        buf.writeUtf(msg.itemId, 128);
        buf.writeVarInt(msg.amount);
        buf.writeEnum(msg.type);
        buf.writeUtf(msg.assigneeName, 256);
        buf.writeEnum(msg.kind);
        buf.writeBoolean(msg.requireReview);
        buf.writeBoolean(msg.location != null);
        if (msg.location != null) {
            buf.writeResourceLocation(msg.location.dimension().location());
            buf.writeBlockPos(msg.location.min());
            buf.writeBlockPos(msg.location.max());
        }
    }

    public static CreateQuestPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String title = buf.readUtf(Quest.MAX_TITLE);
        String description = buf.readUtf(Quest.MAX_DESC);
        String itemId = buf.readUtf(128);
        int amount = buf.readVarInt();
        QuestType type = buf.readEnum(QuestType.class);
        String assigneeName = buf.readUtf(256);
        QuestKind kind = buf.readEnum(QuestKind.class);
        boolean requireReview = buf.readBoolean();
        QuestLocation location = null;
        if (buf.readBoolean()) {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
            BlockPos a = buf.readBlockPos();
            BlockPos b = buf.readBlockPos();
            location = QuestLocation.of(dimension, a, b);
        }
        return new CreateQuestPacket(pos, title, description, itemId, amount, type, assigneeName, kind, requireReview, location);
    }

    public static void handle(CreateQuestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            Level level = player.level();
            if (!level.isLoaded(msg.pos)) return;
            if (player.distanceToSqr(Vec3.atCenterOf(msg.pos)) > 64.0) return;
            if (!(level.getBlockEntity(msg.pos) instanceof QuestTableBlockEntity table)) return;
            if (!table.canManage(player.getUUID())) return;

            String title = SharedConstants.filterText(msg.title).trim();
            String description = SharedConstants.filterText(msg.description).trim();
            if (title.isEmpty()) return;

            // предмет і кількість мають сенс лише для квестів на предмети
            ResourceLocation itemId;
            int amount;
            if (msg.kind == QuestKind.ITEMS) {
                if (msg.amount < 1 || msg.amount > Quest.MAX_AMOUNT) return;
                itemId = ResourceLocation.tryParse(msg.itemId.trim());
                if (itemId == null || !ForgeRegistries.ITEMS.containsKey(itemId)) return;
                if (ForgeRegistries.ITEMS.getValue(itemId) == Items.AIR) return;
                amount = msg.amount;
            } else {
                itemId = new ResourceLocation("minecraft:air");
                amount = 0;
            }

            // місце роботи (необов'язкове): вимір має існувати, координати в межах світу, область не завелика
            QuestLocation location = null;
            if (msg.location != null) {
                QuestLocation l = msg.location;
                ServerLevel dimensionLevel = player.server.getLevel(l.dimension());
                if (dimensionLevel == null
                        || !dimensionLevel.isInWorldBounds(l.min())
                        || !dimensionLevel.isInWorldBounds(l.max())
                        || !l.fitsLimit()) {
                    return;
                }
                location = l;
            }

            UUID assignee = null;
            String assigneeName = "";
            java.util.List<UUID> groupIds = new java.util.ArrayList<>();
            java.util.List<String> groupNames = new java.util.ArrayList<>();

            if (msg.type == QuestType.PERSONAL) {
                ServerPlayer target = player.server.getPlayerList().getPlayerByName(msg.assigneeName.trim());
                if (target == null) {
                    player.sendSystemMessage(Component.translatable("message.townquest.player_not_found"));
                    return;
                }
                assignee = target.getUUID();
                assigneeName = target.getGameProfile().getName();
            } else if (msg.type == QuestType.GROUP) {
                String raw = msg.assigneeName == null ? "" : msg.assigneeName.trim();
                if (raw.isEmpty()) {
                    player.sendSystemMessage(Component.translatable("message.townquest.group_empty"));
                    return;
                }
                for (String part : raw.split("[,;\s]+")) {
                    String name = part.trim();
                    if (name.isEmpty()) continue;
                    ServerPlayer target = player.server.getPlayerList().getPlayerByName(name);
                    if (target == null) {
                        player.sendSystemMessage(Component.translatable("message.townquest.player_not_found"));
                        return;
                    }
                    if (!groupIds.contains(target.getUUID())) {
                        groupIds.add(target.getUUID());
                        groupNames.add(target.getGameProfile().getName());
                    }
                }
                if (groupIds.size() < 2) {
                    player.sendSystemMessage(Component.translatable("message.townquest.group_min"));
                    return;
                }
            }

            boolean requireReview = msg.kind == QuestKind.ITEMS && msg.requireReview;
            table.addQuest(Quest.create(title, description, itemId, amount,
                    msg.type, assignee, assigneeName, groupIds, groupNames, msg.kind, location, requireReview));
        });
        context.setPacketHandled(true);
    }
}
