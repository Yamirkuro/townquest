package org.example.yamirkuro.townquest.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.example.yamirkuro.townquest.block.entity.QuestTableBlockEntity;
import org.example.yamirkuro.townquest.quest.Quest;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/** Клієнт -> сервер: підтвердити / відхилити (опційно конкретного учасника). */
public class ReviewQuestPacket {

    private final BlockPos pos;
    private final UUID questId;
    private final boolean approve;
    @Nullable
    private final UUID participantId;

    public ReviewQuestPacket(BlockPos pos, UUID questId, boolean approve) {
        this(pos, questId, approve, null);
    }

    public ReviewQuestPacket(BlockPos pos, UUID questId, boolean approve, @Nullable UUID participantId) {
        this.pos = pos;
        this.questId = questId;
        this.approve = approve;
        this.participantId = participantId;
    }

    public static void encode(ReviewQuestPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUUID(msg.questId);
        buf.writeBoolean(msg.approve);
        buf.writeBoolean(msg.participantId != null);
        if (msg.participantId != null) {
            buf.writeUUID(msg.participantId);
        }
    }

    public static ReviewQuestPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID questId = buf.readUUID();
        boolean approve = buf.readBoolean();
        UUID participantId = buf.readBoolean() ? buf.readUUID() : null;
        return new ReviewQuestPacket(pos, questId, approve, participantId);
    }

    public static void handle(ReviewQuestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            Level level = player.level();
            if (!level.isLoaded(msg.pos)) return;
            if (player.distanceToSqr(Vec3.atCenterOf(msg.pos)) > 64.0) return;
            if (!(level.getBlockEntity(msg.pos) instanceof QuestTableBlockEntity table)) return;
            if (!table.canManage(player.getUUID())) return;

            Quest quest = table.findQuest(msg.questId);
            if (quest == null) return;
            String title = quest.getTitle();

            UUID notifyId = msg.participantId != null ? msg.participantId : quest.getTakenBy();

            if (table.review(msg.questId, msg.approve, msg.participantId)) {
                if (notifyId != null) {
                    ServerPlayer target = player.server.getPlayerList().getPlayer(notifyId);
                    if (target != null) {
                        target.sendSystemMessage(Component.translatable(
                                msg.approve ? "message.townquest.review_approved" : "message.townquest.review_rejected",
                                title));
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
