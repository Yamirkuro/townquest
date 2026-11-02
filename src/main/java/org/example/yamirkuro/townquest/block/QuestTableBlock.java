package org.example.yamirkuro.townquest.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.example.yamirkuro.townquest.block.entity.QuestTableBlockEntity;
import org.example.yamirkuro.townquest.data.BoundContainer;
import org.example.yamirkuro.townquest.data.QuestRegistry;
import org.example.yamirkuro.townquest.registry.ModBlockEntities;
import org.example.yamirkuro.townquest.registry.ModItems;
import org.jetbrains.annotations.Nullable;

public class QuestTableBlock extends BaseEntityBlock {

    public QuestTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuestTableBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide
                && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof QuestTableBlockEntity table) {
            table.setOwner(player.getUUID());
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof QuestTableBlockEntity table)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }

        ItemStack held = player.getItemInHand(hand);
        // вставити книгу
        if (held.is(ModItems.QUEST_ARCHIVE.get())) {
            if (table.hasArchive()) {
                player.sendSystemMessage(Component.translatable("message.townquest.archive_already"));
            } else if (table.insertArchive(held, player.getUUID())) {
                player.sendSystemMessage(Component.translatable("message.townquest.archive_inserted"));
            }
            return InteractionResult.CONSUME;
        }

        if (!table.hasArchive()) {
            player.sendSystemMessage(Component.translatable("message.townquest.archive_required"));
            return InteractionResult.CONSUME;
        }

        NetworkHooks.openScreen(serverPlayer, table, pos);
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.QUEST_TABLE.get(), QuestTableBlockEntity::serverTick);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof QuestTableBlockEntity table && table.hasArchive()) {
                ItemStack archive = table.createArchiveDrop();
                if (!archive.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), archive);
                }
            }
            if (level.getServer() != null) {
                QuestRegistry.get(level.getServer()).remove(new BoundContainer(level.dimension(), pos.immutable()));
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
