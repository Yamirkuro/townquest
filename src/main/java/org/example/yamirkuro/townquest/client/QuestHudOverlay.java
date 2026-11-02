package org.example.yamirkuro.townquest.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.example.yamirkuro.townquest.Townquest;
import org.example.yamirkuro.townquest.network.ModNetwork;
import org.example.yamirkuro.townquest.network.packet.RequestBookPacket;
import org.example.yamirkuro.townquest.quest.BookEntry;
import org.example.yamirkuro.townquest.quest.Quest;
import org.example.yamirkuro.townquest.quest.QuestKind;
import org.example.yamirkuro.townquest.quest.QuestStatus;
import org.example.yamirkuro.townquest.registry.ModItems;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HUD-трекер: показує закріплені квести, лише якщо Quest Book в інвентарі.
 * Кожен квест це картка: іконка предмета, назва, предмет, лічильник і анімована смуга прогресу.
 */
@Mod.EventBusSubscriber(modid = Townquest.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class QuestHudOverlay {

    private static final int PANEL_X = 8;
    private static final int PANEL_Y = 40;
    private static final int CARD_W = 172;
    private static final int GAP = 3;

    private static final int BG = 0xC0121418;
    private static final int BORDER = 0xFF2E333B;

    private static final String[] STAR = {
            "....X....",
            "....X....",
            "...XXX...",
            "XXXXXXXXX",
            ".XXXXXXX.",
            "..XXXXX..",
            "..XXXXX..",
            ".XXX.XXX.",
            ".XX...XX."
    };

    private static final String[] CHECK = {
            "......X",
            ".....XX",
            "X...XX.",
            "XX.XX..",
            ".XXX..."
    };

    /** Показана зараз частка смуги по квестах (для плавної анімації). */
    private static final Map<UUID, Float> SHOWN = new HashMap<>();
    private static long lastFrameNanos = 0L;

    private QuestHudOverlay() {}

    private static boolean hasBook(LocalPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.QUEST_BOOK.get())) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(ModItems.QUEST_BOOK.get())) return true;
        }
        return false;
    }

    /**
     * Скільки потрібного предмета зараз в інвентарі самого гравця (рахується на клієнті).
     * Свідомо не показуємо вміст сундуків: інакше трекер можна використати, щоб підглядати за ними здалеку.
     */
    private static int countInInventory(LocalPlayer player, Quest quest) {
        Item item = ForgeRegistries.ITEMS.getValue(quest.getItem());
        if (item == null || item == Items.AIR) return 0;
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) count += stack.getCount();
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    /** Раз на 2 сек просимо свіжі дані, якщо щось трекаємо. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) return;
        if (QuestTracker.count() == 0) return;
        if (!hasBook(player)) return;
        if (player.tickCount % 40 != 0) return;
        ModNetwork.CHANNEL.sendToServer(new RequestBookPacket());
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;
        if (mc.screen != null) return; // не малювати поверх меню
        if (!hasBook(player)) return;

        List<BookEntry> tracked = QuestTracker.trackedEntries();
        if (tracked.isEmpty()) {
            SHOWN.clear();
            return;
        }

        // час між кадрами для плавної анімації, незалежної від FPS
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.016f : Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
        lastFrameNanos = now;

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;

        int x = PANEL_X;
        int y = PANEL_Y;
        y += drawHeader(g, font, x, y, tracked.size()) + GAP;

        Set<UUID> alive = new HashSet<>();
        for (BookEntry entry : tracked) {
            Quest q = entry.quest();
            alive.add(q.getId());
            y += drawCard(g, font, player, entry, x, y, dt) + GAP;
        }
        SHOWN.keySet().retainAll(alive);
    }

    // ---------- малювання ----------

    private static int drawHeader(GuiGraphics g, Font font, int x, int y, int count) {
        int h = 15;
        panel(g, x, y, CARD_W, h, BG, BORDER);
        drawPattern(g, STAR, x + 5, y + 3, 0xFFFFD83A);
        g.drawString(font, Component.translatable("gui.townquest.hud.title"), x + 18, y + 4, 0xFFD27F, true);
        String n = count + "/" + QuestTracker.MAX_TRACKED;
        g.drawString(font, n, x + CARD_W - 6 - font.width(n), y + 4, 0x8A9099, true);
        return h;
    }

    /** Малює картку квесту й повертає її висоту. */
    private static int drawCard(GuiGraphics g, Font font, LocalPlayer player, BookEntry entry,
                                int x, int y, float dt) {
        Quest q = entry.quest();
        // предмети: лічильник у інвентарі + здано в сундук; ручний — статус
        boolean counting = q.getKind() == QuestKind.ITEMS && q.getStatus() != QuestStatus.REVIEW;
        int required = q.getRequired();
        int inv = counting ? countInInventory(player, q) : 0;
        int delivered = q.getDelivered();
        int have = counting ? Math.max(inv, delivered) : 0;
        boolean ready = counting && required > 0 && (inv >= required || delivered >= required);

        boolean hasChest = entry.chestPositions() != null && !entry.chestPositions().isEmpty();
        int h = counting ? (hasChest ? 42 : 33) : (hasChest ? 34 : 26);
        int accent = 0xFF000000 | (ready ? 0x55FF55 : QuestUi.statusColor(q));
        int border = BORDER;
        if (ready) {
            // м'яке пульсування рамки, коли все потрібне вже з собою
            int a = 110 + (int) (90 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 220.0)));
            border = (a << 24) | 0x55FF55;
        }

        panel(g, x, y, CARD_W, h, BG, border);
        g.fill(x, y + 3, x + 2, y + h - 3, accent);

        // слот з іконкою предмета, який треба знайти
        int slotX = x + 6;
        int slotY = y + (h - 18) / 2;
        panel(g, slotX, slotY, 18, 18, 0xFF0B0D10, 0xFF3A404A);
        g.renderItem(QuestUi.icon(q), slotX + 1, slotY + 1);

        int tx = x + 30;
        int right = x + CARD_W - 6;

        // назва квесту
        g.drawString(font, font.plainSubstrByWidth(q.getTitle(), right - tx), tx, y + 4, 0xFFFFFF, true);

        // другий рядок: назва предмета зліва, лічильник або статус справа
        String rightText;
        int rightColor;
        if (counting) {
            rightText = delivered + "/" + required;
            if (inv > 0 && inv != delivered) {
                rightText = inv + "·" + delivered + "/" + required;
            }
            rightColor = ready ? 0x55FF55 : 0xFFFFFF;
        } else {
            rightText = QuestUi.statusLabel(q).getString();
            rightColor = QuestUi.statusColor(q);
        }
        int rightW = font.width(rightText);
        int checkW = ready ? 10 : 0;

        String sub = QuestUi.subtitle(q).getString();
        int subMax = Math.max(10, right - tx - rightW - checkW - 8);
        g.drawString(font, font.plainSubstrByWidth(sub, subMax), tx, y + 15, QuestUi.subtitleColor(q), true);

        g.drawString(font, rightText, right - rightW, y + 15, rightColor, true);
        if (ready) {
            drawPattern(g, CHECK, right - rightW - 10, y + 16, 0xFF55FF55);
        }

        // смуга прогресу
        if (counting) {
            int bx = tx;
            int by = y + 26;
            int bw = right - tx;
            float target = required <= 0 ? 0f : Math.min(1f, have / (float) required);
            float shown = SHOWN.getOrDefault(q.getId(), target);
            shown += (target - shown) * (1f - (float) Math.exp(-12.0 * dt));
            if (Math.abs(target - shown) < 0.002f) shown = target;
            SHOWN.put(q.getId(), shown);

            int fw = Math.round(bw * shown);
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 5, 0xFF000000);
            g.fill(bx, by, bx + bw, by + 4, 0xFF1C1F24);
            if (fw > 0) {
                g.fill(bx, by, bx + fw, by + 4, ready ? 0xFF4CD04C : 0xFF3FA7D6);
                g.fill(bx, by, bx + fw, by + 1, ready ? 0xFF8BE88B : 0xFF6CC4EE);
            }
        }
        if (hasChest) {
            var p = entry.chestPositions().get(0);
            String chest = "→ " + p.getX() + " " + p.getY() + " " + p.getZ();
            if (entry.chestPositions().size() > 1) {
                chest += " ×" + entry.chestPositions().size();
            }
            int cy = counting ? y + 32 : y + 24;
            g.drawString(font, font.plainSubstrByWidth(chest, right - tx), tx, cy, 0xFFD27F, true);
        }
        return h;
    }

    /** Прямокутник із «скругленими» кутами: у кутах не малюємо по пікселю. */
    private static void panel(GuiGraphics g, int x, int y, int w, int h, int bg, int border) {
        g.fill(x + 1, y, x + w - 1, y + h, bg);
        g.fill(x, y + 1, x + w, y + h - 1, bg);
        g.fill(x + 1, y, x + w - 1, y + 1, border);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
    }

    private static void drawPattern(GuiGraphics g, String[] rows, int x, int y, int color) {
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                if (rows[r].charAt(c) == 'X') {
                    g.fill(x + c, y + r, x + c + 1, y + r + 1, color);
                }
            }
        }
    }
}