package org.example.yamirkuro.townquest.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.example.yamirkuro.townquest.block.entity.QuestTableBlockEntity;

import java.util.UUID;
import java.util.function.Supplier;

/** Клієнт -> сервер: видалити квест зі столу. */
public class DeleteQuestPacket {

    private final BlockPos pos;
    private final UUID questId;

    public DeleteQuestPacket(BlockPos pos, UUID questId) {
        this.pos = pos;
        this.questId = questId;
    }

    public static void encode(DeleteQuestPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUUID(msg.questId);
    }

    public static DeleteQuestPacket decode(FriendlyByteBuf buf) {
        return new DeleteQuestPacket(buf.readBlockPos(), buf.readUUID());
    }

    public static void handle(DeleteQuestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            Level level = player.level();
            if (!level.isLoaded(msg.pos)) return;
            if (player.distanceToSqr(Vec3.atCenterOf(msg.pos)) > 64.0) return;
            if (!(level.getBlockEntity(msg.pos) instanceof QuestTableBlockEntity table)) return;
            if (!table.canManage(player.getUUID())) return;

            table.removeQuest(msg.questId);
        });
        context.setPacketHandled(true);
    }
}
