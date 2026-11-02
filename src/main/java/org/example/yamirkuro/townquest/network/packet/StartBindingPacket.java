package org.example.yamirkuro.townquest.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.example.yamirkuro.townquest.block.entity.QuestTableBlockEntity;
import org.example.yamirkuro.townquest.event.BindingManager;

import java.util.function.Supplier;

/** Клієнт -> сервер: увімкнути режим прив'язки сундука для цього столу. */
public class StartBindingPacket {

    private final BlockPos pos;

    public StartBindingPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(StartBindingPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static StartBindingPacket decode(FriendlyByteBuf buf) {
        return new StartBindingPacket(buf.readBlockPos());
    }

    public static void handle(StartBindingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            Level level = player.level();
            if (!level.isLoaded(msg.pos)) return;
            if (player.distanceToSqr(Vec3.atCenterOf(msg.pos)) > 64.0) return;
            if (!(level.getBlockEntity(msg.pos) instanceof QuestTableBlockEntity table)) return;
            if (!table.canManage(player.getUUID())) return;

            BindingManager.start(player.getUUID(), new BindingManager.Session(
                    msg.pos, level.dimension(), level.getGameTime() + BindingManager.TIMEOUT_TICKS));
            player.displayClientMessage(Component.translatable("message.townquest.binding_started"), true);
        });
        context.setPacketHandled(true);
    }
}
