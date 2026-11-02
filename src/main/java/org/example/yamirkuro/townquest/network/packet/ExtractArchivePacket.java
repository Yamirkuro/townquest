package org.example.yamirkuro.townquest.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.example.yamirkuro.townquest.block.entity.QuestTableBlockEntity;

import java.util.function.Supplier;

/** Клієнт -> сервер: вийняти головну книгу зі столу. */
public class ExtractArchivePacket {

    private final BlockPos pos;

    public ExtractArchivePacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(ExtractArchivePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static ExtractArchivePacket decode(FriendlyByteBuf buf) {
        return new ExtractArchivePacket(buf.readBlockPos());
    }

    public static void handle(ExtractArchivePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            Level level = player.level();
            if (!level.isLoaded(msg.pos)) return;
            if (player.distanceToSqr(Vec3.atCenterOf(msg.pos)) > 64.0) return;
            if (!(level.getBlockEntity(msg.pos) instanceof QuestTableBlockEntity table)) return;
            if (!table.canManage(player.getUUID())) return;
            if (!table.hasArchive()) return;

            ItemStack archive = table.extractArchive();
            if (!archive.isEmpty()) {
                if (!player.getInventory().add(archive)) {
                    player.drop(archive, false);
                }
                player.sendSystemMessage(Component.translatable("message.townquest.archive_extracted"));
                player.closeContainer();
            }
        });
        context.setPacketHandled(true);
    }
}
