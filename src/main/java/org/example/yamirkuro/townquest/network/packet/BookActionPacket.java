package org.example.yamirkuro.townquest.network.packet;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.example.yamirkuro.townquest.data.BoundContainer;
import org.example.yamirkuro.townquest.service.QuestBookService;

import java.util.UUID;
import java.util.function.Supplier;

/** Клієнт -> сервер: «взяти», «відмовитись» або «завершити» квест з Quest Book. */
public class BookActionPacket {

    public static final byte TAKE = 0;
    public static final byte RELEASE = 1;
    public static final byte SUBMIT = 2;

    private final BoundContainer table;
    private final UUID questId;
    private final byte action;

    public BookActionPacket(BoundContainer table, UUID questId, byte action) {
        this.table = table;
        this.questId = questId;
        this.action = action;
    }

    public static void encode(BookActionPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.table.dimension().location());
        buf.writeBlockPos(msg.table.pos());
        buf.writeUUID(msg.questId);
        buf.writeByte(msg.action);
    }

    public static BookActionPacket decode(FriendlyByteBuf buf) {
        BoundContainer table = new BoundContainer(
                ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation()),
                buf.readBlockPos());
        return new BookActionPacket(table, buf.readUUID(), buf.readByte());
    }

    public static void handle(BookActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !QuestBookService.hasBook(player)) return;

            switch (msg.action) {
                case TAKE -> QuestBookService.take(player, msg.table, msg.questId);
                case RELEASE -> QuestBookService.release(player, msg.table, msg.questId);
                case SUBMIT -> QuestBookService.submit(player, msg.table, msg.questId);
                default -> {
                    return;
                }
            }
            QuestBookService.send(player, false);
        });
        context.setPacketHandled(true);
    }
}
