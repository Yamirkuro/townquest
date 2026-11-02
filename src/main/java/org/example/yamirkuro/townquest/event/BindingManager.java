package org.example.yamirkuro.townquest.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/** Тимчасовий «режим прив'язки сундука» для гравця (лише на сервері, не зберігається). */
public final class BindingManager {

    public static final long TIMEOUT_TICKS = 20L * 30L;

    public record Session(BlockPos tablePos, ResourceKey<Level> tableDim, long expireTick) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private BindingManager() {}

    public static void start(UUID player, Session session) {
        SESSIONS.put(player, session);
    }

    @Nullable
    public static Session get(UUID player) {
        return SESSIONS.get(player);
    }

    public static void end(UUID player) {
        SESSIONS.remove(player);
    }

    public static void clear() {
        SESSIONS.clear();
    }
}
