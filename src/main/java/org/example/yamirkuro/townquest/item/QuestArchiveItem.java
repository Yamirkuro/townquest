package org.example.yamirkuro.townquest.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.example.yamirkuro.townquest.data.BoundContainer;
import org.example.yamirkuro.townquest.quest.Quest;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Головна книга столу: зберігає квести, сундуки, owner і trusted. */
public class QuestArchiveItem extends Item {

    public QuestArchiveItem(Properties properties) {
        super(properties);
    }

    public static CompoundTag writeData(@Nullable UUID owner, Set<UUID> trusted,
                                        List<Quest> quests, List<BoundContainer> containers) {
        CompoundTag tag = new CompoundTag();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        ListTag trustedList = new ListTag();
        for (UUID id : trusted) {
            trustedList.add(NbtUtils.createUUID(id));
        }
        tag.put("Trusted", trustedList);

        ListTag questList = new ListTag();
        for (Quest quest : quests) {
            questList.add(quest.save());
        }
        tag.put("Quests", questList);

        ListTag containerList = new ListTag();
        for (BoundContainer c : containers) {
            containerList.add(c.save());
        }
        tag.put("Containers", containerList);
        return tag;
    }

    public static void applyToStack(ItemStack stack, @Nullable UUID owner, Set<UUID> trusted,
                                    List<Quest> quests, List<BoundContainer> containers) {
        stack.getOrCreateTag().put("Archive", writeData(owner, trusted, quests, containers));
    }

    @Nullable
    public static CompoundTag getArchiveTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("Archive", Tag.TAG_COMPOUND)) return null;
        return tag.getCompound("Archive");
    }

    public static int questCount(ItemStack stack) {
        CompoundTag archive = getArchiveTag(stack);
        if (archive == null) return 0;
        return archive.getList("Quests", Tag.TAG_COMPOUND).size();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.townquest.archive"));
        int n = questCount(stack);
        tip.add(Component.translatable("tooltip.townquest.archive_quests", n));
    }
}
