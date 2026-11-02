package org.example.yamirkuro.townquest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.example.yamirkuro.townquest.service.QuestBookService;

import java.util.function.Supplier;

/** Клієнт -> сервер: оновити дані відкритої Quest Book. */
public class RequestBookPacket {

    public RequestBookPacket() {}

    public static void encode(RequestBookPacket msg, FriendlyByteBuf buf) {}

    public static RequestBookPacket decode(FriendlyByteBuf buf) {
        return new RequestBookPacket();
    }

    public static void handle(RequestBookPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !QuestBookService.hasBook(player)) return;
            QuestBookService.send(player, false);
        });
        context.setPacketHandled(true);
    }
}
