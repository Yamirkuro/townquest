package org.example.yamirkuro.townquest.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.example.yamirkuro.townquest.quest.QuestLocation;
import org.example.yamirkuro.townquest.registry.ModItems;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Глобус: ПКМ по блоку ставить кут A / кут B області квесту.
 * Підсвітка сундуків (пізніше) працює, якщо глобус є в інвентарі.
 */
public class QuestGlobeItem extends Item {

    public static final String TAG_DIM = "Dim";
    public static final String TAG_A = "CornerA";
    public static final String TAG_B = "CornerB";

    public QuestGlobeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = ctx.getItemInHand();
        BlockPos clicked = ctx.getClickedPos();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        CompoundTag tag = stack.getOrCreateTag();
        boolean hasA = tag.contains(TAG_A);
        boolean hasB = tag.contains(TAG_B);

        if (!hasA || hasB) {
            // новий вибір: скинути B, поставити A
            tag.putLong(TAG_A, clicked.asLong());
            tag.remove(TAG_B);
            tag.putString(TAG_DIM, level.dimension().location().toString());
            player.displayClientMessage(Component.translatable(
                    "message.townquest.globe_corner_a",
                    clicked.getX(), clicked.getY(), clicked.getZ()), true);
        } else {
            // є лише A → ставимо B
            BlockPos a = BlockPos.of(tag.getLong(TAG_A));
            tag.putLong(TAG_B, clicked.asLong());
            tag.putString(TAG_DIM, level.dimension().location().toString());
            QuestLocation loc = QuestLocation.of(level.dimension(), a, clicked);
            player.displayClientMessage(Component.translatable(
                    "message.townquest.globe_corner_b",
                    clicked.getX(), clicked.getY(), clicked.getZ(),
                    loc.sizeX(), loc.sizeY(), loc.sizeZ()), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.townquest.globe"));
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        if (tag.contains(TAG_A)) {
            BlockPos a = BlockPos.of(tag.getLong(TAG_A));
            tip.add(Component.translatable("tooltip.townquest.globe_a", a.getX(), a.getY(), a.getZ()));
        }
        if (tag.contains(TAG_B)) {
            BlockPos b = BlockPos.of(tag.getLong(TAG_B));
            tip.add(Component.translatable("tooltip.townquest.globe_b", b.getX(), b.getY(), b.getZ()));
        }
    }

    /** Чи є глобус у інвентарі (для підсвітки пізніше). */
    public static boolean hasInInventory(Player player) {
        Inventory inv = player.getInventory();
        for (ItemStack stack : inv.items) {
            if (stack.is(ModItems.QUEST_GLOBE.get())) return true;
        }
        for (ItemStack stack : inv.offhand) {
            if (stack.is(ModItems.QUEST_GLOBE.get())) return true;
        }
        return false;
    }

    /** Знайти глобус у інвентарі гравця (спочатку рука, потім решта). */
    @Nullable
    public static ItemStack findInInventory(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.QUEST_GLOBE.get())) return main;
        ItemStack off = player.getOffhandItem();
        if (off.is(ModItems.QUEST_GLOBE.get())) return off;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.QUEST_GLOBE.get())) return stack;
        }
        return null;
    }

    /** Зчитати область з NBT глобуса. null якщо кутів немає. */
    @Nullable
    public static QuestLocation readLocation(ItemStack stack, ResourceKey<Level> fallbackDim) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_A)) return null;
        BlockPos a = BlockPos.of(tag.getLong(TAG_A));
        BlockPos b = tag.contains(TAG_B) ? BlockPos.of(tag.getLong(TAG_B)) : a;
        ResourceKey<Level> dim = fallbackDim;
        if (tag.contains(TAG_DIM)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_DIM));
            if (id != null) {
                dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
            }
        }
        return QuestLocation.of(dim, a, b);
    }
}
