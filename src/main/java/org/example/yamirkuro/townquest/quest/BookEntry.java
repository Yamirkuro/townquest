package org.example.yamirkuro.townquest.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.example.yamirkuro.townquest.data.BoundContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Один рядок у Quest Book: квест + контекст столу (хто видав, де стіл, куди здавати).
 */
public record BookEntry(
        BoundContainer table,
        Quest quest,
        String ownerName,
        List<BlockPos> chestPositions
) {
    public BookEntry {
        ownerName = ownerName == null ? "" : ownerName;
        chestPositions = List.copyOf(chestPositions == null ? List.of() : chestPositions);
    }

    /** Старий конструктор на 2 аргументи — для сумісності, без контексту. */
    public BookEntry(BoundContainer table, Quest quest) {
        this(table, quest, "", List.of());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Table", table.save());
        tag.put("Quest", quest.save());
        tag.putString("OwnerName", ownerName);
        ListTag chests = new ListTag();
        for (BlockPos pos : chestPositions) {
            CompoundTag p = new CompoundTag();
            p.putLong("Pos", pos.asLong());
            chests.add(p);
        }
        tag.put("Chests", chests);
        return tag;
    }

    public static BookEntry load(CompoundTag tag) {
        BoundContainer table = BoundContainer.load(tag.getCompound("Table"));
        Quest quest = Quest.load(tag.getCompound("Quest"));
        String ownerName = tag.getString("OwnerName");
        List<BlockPos> chests = new ArrayList<>();
        for (Tag t : tag.getList("Chests", Tag.TAG_COMPOUND)) {
            chests.add(BlockPos.of(((CompoundTag) t).getLong("Pos")));
        }
        return new BookEntry(table, quest, ownerName, chests);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(save());
    }

    public static BookEntry decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return tag == null ? new BookEntry(
                new BoundContainer(Level.OVERWORLD, BlockPos.ZERO),
                Quest.load(new CompoundTag()),
                "",
                List.of()) : load(tag);
    }
}
