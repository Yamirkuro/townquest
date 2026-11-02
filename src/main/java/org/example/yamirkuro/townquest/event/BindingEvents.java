package org.example.yamirkuro.townquest.event;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.example.yamirkuro.townquest.Townquest;
import org.example.yamirkuro.townquest.block.entity.QuestTableBlockEntity;
import org.example.yamirkuro.townquest.data.BoundContainer;

@Mod.EventBusSubscriber(modid = Townquest.MODID)
public class BindingEvents {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BindingManager.Session session = BindingManager.get(player.getUUID());
        if (session == null) return;

        ServerLevel level = player.serverLevel();
        if (level.getGameTime() > session.expireTick()) {
            BindingManager.end(player.getUUID());
            return;
        }

        // Поки діє режим прив'язки, клік не відкриває сундук і не ставить блок
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        ServerLevel tableLevel = level.getServer().getLevel(session.tableDim());
        if (tableLevel == null || !tableLevel.isLoaded(session.tablePos())
                || !(tableLevel.getBlockEntity(session.tablePos()) instanceof QuestTableBlockEntity table)) {
            BindingManager.end(player.getUUID());
            return;
        }

        BlockPos target = event.getPos();

        // Клік по самому столу скасовує режим
        if (level.dimension() == session.tableDim() && target.equals(session.tablePos())) {
            BindingManager.end(player.getUUID());
            say(player, "message.townquest.binding_cancelled");
            return;
        }

        if (!table.canManage(player.getUUID())) {
            BindingManager.end(player.getUUID());
            return;
        }

        BlockEntity be = level.getBlockEntity(target);
        if (be == null || !be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()) {
            say(player, "message.townquest.binding_not_container");
            return;
        }

        QuestTableBlockEntity.BindResult result =
                table.toggleContainer(new BoundContainer(level.dimension(), target.immutable()));
        int count = table.getContainers().size();
        int max = QuestTableBlockEntity.MAX_CONTAINERS;
        switch (result) {
            case ADDED -> player.displayClientMessage(
                    Component.translatable("message.townquest.binding_added", count, max), true);
            case REMOVED -> player.displayClientMessage(
                    Component.translatable("message.townquest.binding_removed", count, max), true);
            case LIMIT_REACHED -> player.displayClientMessage(
                    Component.translatable("message.townquest.binding_limit", max), true);
        }
        BindingManager.end(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BindingManager.end(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BindingManager.clear();
    }

    private static void say(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
