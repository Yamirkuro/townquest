package org.example.yamirkuro.townquest.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.example.yamirkuro.townquest.registry.ModBlocks;
import org.example.yamirkuro.townquest.registry.ModMenus;

/**
 * Меню без слотів: потрібне лише щоб клієнт і сервер знали, який стіл відкрито.
 * Самі квести клієнт бере із синхронізованого BlockEntity.
 */
public class QuestTableMenu extends AbstractContainerMenu {

    private final BlockPos pos;

    public QuestTableMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenus.QUEST_TABLE.get(), containerId);
        this.pos = pos;
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(player.level(), pos), player, ModBlocks.QUEST_TABLE.get());
    }
}
