package org.example.yamirkuro.townquest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.example.yamirkuro.townquest.Townquest;
import org.example.yamirkuro.townquest.block.entity.QuestTableBlockEntity;
import org.example.yamirkuro.townquest.data.BoundContainer;
import org.example.yamirkuro.townquest.item.QuestGlobeItem;
import org.example.yamirkuro.townquest.quest.BookEntry;
import org.example.yamirkuro.townquest.quest.QuestLocation;
import org.example.yamirkuro.townquest.registry.ModItems;

import java.util.ArrayList;
import java.util.List;

/**
 * Підсвітка у світі (усе малюється лише на клієнті, сервер не навантажується).
 *
 * <ul>
 *   <li>Сундуки здачі, коли в інвентарі є Глобус: ≤16 блоків контур, ≤32 частинки.</li>
 *   <li>Область квесту (каркас із 12 ліній): для квестів, які відстежуються зірочкою, поки Книга в інвентарі,
 *       і для вибору Глобуса. Малюється лише зблизька.</li>
 *   <li>Одна точка квесту: контур блока (≤16) і стовп частинок (≤32), як у сундуків.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Townquest.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class QuestHighlightHandler {

    private static final double OUTLINE_RANGE = 16.0;
    private static final double PARTICLE_RANGE = 32.0;
    private static final double SCAN_RANGE = 48.0;

    /** Область малюємо, лише коли гравець ближче цієї відстані до її поверхні. */
    private static final double AREA_RANGE = 48.0;

    private QuestHighlightHandler() {}

    /** Що саме підсвічувати: область або точка, колір, і чи це квест (тоді для точки ще й частинки). */
    private record Marker(QuestLocation loc, float r, float g, float b, boolean quest) {}

    // ---------- допоміжне ----------

    private static boolean hasBook(LocalPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.QUEST_BOOK.get())) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(ModItems.QUEST_BOOK.get())) return true;
        }
        return false;
    }

    private static List<BlockPos> collectNearbyChests(LocalPlayer player) {
        Level level = player.level();
        List<BlockPos> result = new ArrayList<>();
        BlockPos origin = player.blockPosition();
        int chunkR = ((int) SCAN_RANGE >> 4) + 1;
        int cx0 = origin.getX() >> 4;
        int cz0 = origin.getZ() >> 4;

        for (int cx = cx0 - chunkR; cx <= cx0 + chunkR; cx++) {
            for (int cz = cz0 - chunkR; cz <= cz0 + chunkR; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof QuestTableBlockEntity table)) continue;
                    if (!table.hasArchive()) continue;
                    if (!be.getBlockPos().closerToCenterThan(player.position(), SCAN_RANGE)) continue;
                    for (BoundContainer c : table.getContainers()) {
                        if (!c.dimension().equals(level.dimension())) continue;
                        result.add(c.pos());
                    }
                }
            }
        }
        return result;
    }

    /**
     * Зони у світі — ТІЛЬКИ якщо є Глобус:
     *  - області/точки взятих тобою квестів (IN_PROGRESS / REVIEW);
     *  - поточний вибір кутів глобуса (A/B).
     * ★ трекер на рамки не впливає.
     */
    private static List<Marker> collectMarkers(LocalPlayer player, Level level) {
        List<Marker> markers = new ArrayList<>();
        if (!QuestGlobeItem.hasInInventory(player)) {
            return markers;
        }

        java.util.UUID me = player.getUUID();
        for (BookEntry entry : ClientQuestCache.all()) {
            var q = entry.quest();
            QuestLocation loc = q.getLocation();
            if (loc == null || !loc.dimension().equals(level.dimension())) continue;
            var st = q.statusFor(me);
            if (st == org.example.yamirkuro.townquest.quest.QuestStatus.IN_PROGRESS
                    || st == org.example.yamirkuro.townquest.quest.QuestStatus.REVIEW) {
                markers.add(new Marker(loc, 0.35f, 0.85f, 1.0f, true));
            }
        }

        ItemStack globe = QuestGlobeItem.findInInventory(player);
        if (globe != null) {
            QuestLocation selection = QuestGlobeItem.readLocation(globe, level.dimension());
            if (selection != null && selection.dimension().equals(level.dimension())) {
                markers.add(new Marker(selection, 0.4f, 1.0f, 0.45f, false));
            }
        }
        return markers;
    }

    /** Відстань від гравця до найближчої точки області (0, якщо він усередині). */
    private static double distanceToArea(Vec3 p, QuestLocation loc) {
        double dx = Math.max(Math.max(loc.min().getX() - p.x, 0.0), p.x - (loc.max().getX() + 1));
        double dy = Math.max(Math.max(loc.min().getY() - p.y, 0.0), p.y - (loc.max().getY() + 1));
        double dz = Math.max(Math.max(loc.min().getZ() - p.z, 0.0), p.z - (loc.max().getZ() + 1));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---------- частинки ----------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || mc.isPaused()) return;

        // з глобусом раз на 2 с оновлюємо список квестів (зони взятих)
        if (QuestGlobeItem.hasInInventory(player) && player.tickCount % 40 == 0) {
            org.example.yamirkuro.townquest.network.ModNetwork.CHANNEL.sendToServer(
                    new org.example.yamirkuro.townquest.network.packet.RequestBookPacket());
        }

        if (player.tickCount % 10 != 0) return;

        RandomSource random = level.random;

        // сундуки здачі: лише з Глобусом
        if (QuestGlobeItem.hasInInventory(player)) {
            for (BlockPos pos : collectNearbyChests(player)) {
                double dist = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(pos)));
                if (dist > PARTICLE_RANGE) continue;
                double x = pos.getX() + 0.5;
                double y = pos.getY() + 1.15;
                double z = pos.getZ() + 0.5;
                level.addParticle(ParticleTypes.END_ROD,
                        x + (random.nextDouble() - 0.5) * 0.3,
                        y + random.nextDouble() * 0.25,
                        z + (random.nextDouble() - 0.5) * 0.3,
                        0.0, 0.02, 0.0);
                if (dist <= OUTLINE_RANGE) {
                    level.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y, z, 0.0, 0.0, 0.0);
                }
            }
        }

        // одна точка квесту: стовп світлових частинок
        for (Marker marker : collectMarkers(player, level)) {
            if (!marker.quest() || marker.loc().isArea()) continue;
            BlockPos pos = marker.loc().min();
            double dist = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(pos)));
            if (dist > PARTICLE_RANGE) continue;
            for (int i = 0; i < 3; i++) {
                level.addParticle(ParticleTypes.END_ROD,
                        pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5,
                        pos.getY() + 0.1 + random.nextDouble() * 2.0,
                        pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5,
                        0.0, 0.03, 0.0);
            }
        }
    }

    // ---------- контури й каркаси ----------

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        boolean globe = QuestGlobeItem.hasInInventory(player);
        if (!globe) return; // без глобуса нічого не малюємо у світі
        List<Marker> markers = collectMarkers(player, level);

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        if (globe) {
            for (BlockPos pos : collectNearbyChests(player)) {
                double dist = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(pos)));
                if (dist > OUTLINE_RANGE) continue;
                AABB box = new AABB(pos).inflate(0.002);
                LevelRenderer.renderLineBox(pose, lines, box, 1.0f, 0.85f, 0.15f, 0.9f);
            }
        }

        Vec3 playerPos = player.position();
        for (Marker marker : markers) {
            QuestLocation loc = marker.loc();
            AABB box;
            if (loc.isArea()) {
                // область: каркас із 12 ліній, лише зблизька
                if (distanceToArea(playerPos, loc) > AREA_RANGE) continue;
                box = new AABB(
                        loc.min().getX(), loc.min().getY(), loc.min().getZ(),
                        loc.max().getX() + 1, loc.max().getY() + 1, loc.max().getZ() + 1).inflate(0.002);
            } else {
                // точка: контур одного блока
                BlockPos pos = loc.min();
                if (Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(pos))) > OUTLINE_RANGE) continue;
                box = new AABB(pos).inflate(0.002);
            }
            LevelRenderer.renderLineBox(pose, lines, box, marker.r(), marker.g(), marker.b(), 0.95f);
        }

        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }
}