package org.example.yamirkuro.townquest.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.example.yamirkuro.townquest.client.ClientPacketHandler;
import org.example.yamirkuro.townquest.quest.BookEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Сервер -> клієнт: список квестів для Quest Book. open=true відкриває книгу, false лише оновлює. */
public class BookDataPacket {

    private final boolean open;
    private final List<BookEntry> entries;

    public BookDataPacket(boolean open, List<BookEntry> entries) {
        this.open = open;
        this.entries = entries;
    }

    public static void encode(BookDataPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.open);
        ListTag list = new ListTag();
        for (BookEntry entry : msg.entries) {
            list.add(entry.save());
        }
        CompoundTag root = new CompoundTag();
        root.put("Entries", list);
        buf.writeNbt(root);
    }

    public static BookDataPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        CompoundTag root = buf.readNbt();
        List<BookEntry> entries = new ArrayList<>();
        if (root != null) {
            for (Tag t : root.getList("Entries", Tag.TAG_COMPOUND)) {
                entries.add(BookEntry.load((CompoundTag) t));
            }
        }
        return new BookDataPacket(open, entries);
    }

    public static void handle(BookDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.onBookData(msg.open, msg.entries)));
        context.setPacketHandled(true);
    }
}
