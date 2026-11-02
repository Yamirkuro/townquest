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
import org.example.yamirkuro.townquest.quest.QuestParticipant;

import java.util.UUID;
import java.util.function.Supplier;

/** Власник примусово завершує квест для всіх учасників. */
public class ForceCompletePacket {

    private final BlockPos pos;
    private final UUID questId;

    public ForceCompletePacket(BlockPos pos, UUID questId) {
        this.pos = pos;
        this.questId = questId;
    }

    public static void encode(ForceCompletePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUUID(msg.questId);
    }

    public static ForceCompletePacket decode(FriendlyByteBuf buf) {
        return new ForceCompletePacket(buf.readBlockPos(), buf.readUUID());
    }

    public static void handle(ForceCompletePacket msg, Supplier<NetworkEvent.Context> ctx) {
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

            if (table.forceComplete(msg.questId)) {
                player.sendSystemMessage(Component.translatable("message.townquest.force_complete", title));
                for (QuestParticipant p : quest.getParticipants()) {
                    ServerPlayer target = player.server.getPlayerList().getPlayer(p.getId());
                    if (target != null) {
                        target.sendSystemMessage(Component.translatable("message.townquest.force_complete_player", title));
                    }
                }
                if (quest.getTakenBy() != null && quest.getParticipants().isEmpty()) {
                    ServerPlayer target = player.server.getPlayerList().getPlayer(quest.getTakenBy());
                    if (target != null) {
                        target.sendSystemMessage(Component.translatable("message.townquest.force_complete_player", title));
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
