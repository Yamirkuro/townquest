package org.example.yamirkuro.townquest.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.example.yamirkuro.townquest.client.QuestTracker;
import org.example.yamirkuro.townquest.client.QuestUi;
import org.example.yamirkuro.townquest.network.ModNetwork;
import org.example.yamirkuro.townquest.network.packet.BookActionPacket;
import org.example.yamirkuro.townquest.network.packet.RequestBookPacket;
import org.example.yamirkuro.townquest.quest.BookEntry;
import org.example.yamirkuro.townquest.quest.Quest;
import org.example.yamirkuro.townquest.quest.QuestKind;
import org.example.yamirkuro.townquest.quest.QuestStatus;
import org.example.yamirkuro.townquest.quest.QuestType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Особиста книга завдань гравця. */
public class QuestBookScreen extends Screen {

    private static final int W = 256;
    private static final int H = 196;
    private static final int CARD_H = 44;
    private static final int CARD_GAP = 2;
    private static final int VISIBLE = 3;
    private static final int LIST_TOP = 26;

    private static final int BTN_W = 64;

    private record Action(byte code, String labelKey, int color) {}

    private List<BookEntry> entries;
    private int scroll = 0;
    private int tickCounter = 0;

    public QuestBookScreen(List<BookEntry> entries) {
        super(Component.translatable("item.townquest.quest_book"));
        this.entries = entries;
    }

    public void setEntries(List<BookEntry> entries) {
        this.entries = entries;
        this.scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    // ---------- геометрія ----------

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private int maxScroll() { return Math.max(0, entries.size() - VISIBLE); }

    private UUID me() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : new UUID(0, 0);
    }

    private int cardY(int i) {
        return top() + LIST_TOP + i * (CARD_H + CARD_GAP);
    }

    /** Прямокутник кнопки дії: x, y, ширина, висота. Одна кнопка по центру, дві одна під одною. */
    private int[] buttonRect(int cardX, int cardW, int cy, int index, int count) {
        int x = cardX + cardW - BTN_W - 8;
        if (count == 1) {
            return new int[]{x, cy + 14, BTN_W, 16};
        }
        return new int[]{x, cy + 5 + index * 18, BTN_W, 14};
    }

    private List<Action> actionsFor(Quest q, UUID me) {
        List<Action> list = new ArrayList<>();
        if (q.allowsMultiple()) {
            QuestStatus mine = q.statusFor(me);
            if (mine == QuestStatus.OPEN && q.getStatus() != QuestStatus.DONE) {
                list.add(new Action(BookActionPacket.TAKE, "gui.townquest.book.take", 0xFF3A8A3A));
            } else if (mine == QuestStatus.IN_PROGRESS) {
                if (q.getKind() == QuestKind.MANUAL) {
                    list.add(new Action(BookActionPacket.SUBMIT, "gui.townquest.book.submit", 0xFF3A6A9A));
                }
                list.add(new Action(BookActionPacket.RELEASE, "gui.townquest.book.release", 0xFF8A3A3A));
            } else if (mine == QuestStatus.REVIEW) {
                // чекає перевірку — без кнопок
            }
            return list;
        }
        boolean mine = q.hasParticipant(me) || me.equals(q.getTakenBy());
        switch (q.getStatus()) {
            case OPEN -> list.add(new Action(BookActionPacket.TAKE, "gui.townquest.book.take", 0xFF3A8A3A));
            case IN_PROGRESS -> {
                if (mine) {
                    if (q.getKind() == QuestKind.MANUAL) {
                        list.add(new Action(BookActionPacket.SUBMIT, "gui.townquest.book.submit", 0xFF3A6A9A));
                    }
                    list.add(new Action(BookActionPacket.RELEASE, "gui.townquest.book.release", 0xFF8A3A3A));
                }
            }
            default -> { }
        }
        return list;
    }

    // ---------- життєвий цикл ----------

    @Override
    public void tick() {
        super.tick();
        // раз на 2 секунди просимо в сервера свіжі дані (прогрес змінюється)
        if (++tickCounter % 40 == 0) {
            ModNetwork.CHANNEL.sendToServer(new RequestBookPacket());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------- малювання ----------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int l = left();
        int t = top();
        g.fill(l, t, l + W, t + H, 0xEE141414);
        g.renderOutline(l, t, W, H, 0xFF6B6B6B);
        g.drawString(font, title, l + 8, t + 8, 0xFFFFFF, false);
        Component count = Component.translatable("gui.townquest.book.count", entries.size());
        g.drawString(font, count, l + W - 8 - font.width(count), t + 8, 0xAAAAAA, false);

        if (entries.isEmpty()) {
            Component empty = Component.translatable("gui.townquest.book.empty");
            g.drawString(font, empty, l + (W - font.width(empty)) / 2, t + LIST_TOP + 50, 0x888888, false);
        }

        UUID me = me();
        int x = l + 8;
        int w = W - 16;

        for (int i = 0; i < VISIBLE; i++) {
            int idx = scroll + i;
            if (idx >= entries.size()) break;

            Quest q = entries.get(idx).quest();
            int cy = cardY(i);
            boolean mine = q.hasParticipant(me) || me.equals(q.getTakenBy());
            boolean done = q.getStatus() == QuestStatus.DONE;

            int fill = done ? 0xFF222222 : (mine ? 0xFF22331F : 0xFF2A2A2A);
            int outline = done ? 0xFF3F3F3F : (mine ? 0xFF4C8A44 : 0xFF555555);
            g.fill(x, cy, x + w, cy + CARD_H, fill);
            g.renderOutline(x, cy, w, CARD_H, outline);

            // зірочка трекера
            boolean tracked = QuestTracker.isTracked(q.getId());
            g.drawString(font, tracked ? "★" : "☆", x + 4, cy + 3,
                    tracked ? 0xFFD700 : 0x888888, false);

            ItemStack icon = QuestUi.icon(q);
            g.renderItem(icon, x + 6, cy + 16);

            int titleColor = done ? 0x888888 : (q.getType() == QuestType.PERSONAL ? 0x7FD4FF : 0xFFFFFF);
            g.drawString(font, font.plainSubstrByWidth(q.getTitle(), w - 38), x + 30, cy + 4, titleColor, false);

            Component subtitle = QuestUi.subtitle(q);
            g.drawString(font, subtitle, x + 30, cy + 15, QuestUi.subtitleColor(q), false);
            if (mine && q.getKind() == QuestKind.ITEMS && !done) {
                Component inv = Component.translatable("gui.townquest.book.in_inventory", q.getInInventory());
                int ix = x + 30 + font.width(subtitle) + 6;
                if (ix + font.width(inv) < x + w - BTN_W - 12) {
                    g.drawString(font, inv, ix, cy + 15, 0x7FD4FF, false);
                }
            }

            BookEntry entry = entries.get(idx);
            String context = buildContextLine(entry);
            g.drawString(font, font.plainSubstrByWidth(context, w - 40 - BTN_W - 8),
                    x + 30, cy + 26, 0x9A9A9A, false);

            QuestUi.drawProgressRow(g, font, q, x + 30, cy + 36, w - 130);

            drawActionArea(g, q, me, x, w, cy, mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);

        // підказка з деталями при наведенні на картку
        for (int i = 0; i < VISIBLE; i++) {
            int idx = scroll + i;
            if (idx >= entries.size()) break;
            int cy = cardY(i);
            if (mouseX >= x && mouseX < x + w && mouseY >= cy && mouseY < cy + CARD_H) {
                QuestUi.renderQuestTooltip(g, font, entries.get(idx), mouseX, mouseY);
                break;
            }
        }
    }

    private void drawActionArea(GuiGraphics g, Quest q, UUID me, int cardX, int cardW, int cy,
                                int mouseX, int mouseY) {
        List<Action> actions = actionsFor(q, me);
        if (!actions.isEmpty()) {
            for (int i = 0; i < actions.size(); i++) {
                Action action = actions.get(i);
                int[] r = buttonRect(cardX, cardW, cy, i, actions.size());
                boolean hover = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
                g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hover ? brighten(action.color()) : action.color());
                g.renderOutline(r[0], r[1], r[2], r[3], 0xFF000000);
                g.drawCenteredString(font, Component.translatable(action.labelKey()),
                        r[0] + r[2] / 2, r[1] + (r[3] - 8) / 2, 0xFFFFFF);
            }
        } else if (q.getTakenBy() != null) {
            // квест виконує (або виконав) гравець: голова й нік
            int px = cardX + cardW - BTN_W - 8;
            QuestUi.drawHead(g, q.getTakenBy(), px, cy + 16, 12);
            g.drawString(font, font.plainSubstrByWidth(q.getTakenByName(), BTN_W - 16),
                    px + 16, cy + 18, 0xAAAAAA, false);
        }
    }

    private static int brighten(int argb) {
        int a = argb & 0xFF000000;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 40);
        int gr = Math.min(255, ((argb >> 8) & 0xFF) + 40);
        int b = Math.min(255, (argb & 0xFF) + 40);
        return a | (r << 16) | (gr << 8) | b;
    }

    // ---------- ввід ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            UUID me = me();
            int x = left() + 8;
            int w = W - 16;
            for (int i = 0; i < VISIBLE; i++) {
                int idx = scroll + i;
                if (idx >= entries.size()) break;

                BookEntry entry = entries.get(idx);
                Quest q = entry.quest();
                int cy = cardY(i);
                // клік по зірочці
                if (mouseX >= x + 2 && mouseX < x + 14 && mouseY >= cy + 2 && mouseY < cy + 14) {
                    if (!QuestTracker.toggle(q.getId()) && !QuestTracker.isTracked(q.getId())) {
                        // ліміт трекера
                    }
                    return true;
                }
                List<Action> actions = actionsFor(q, me);
                for (int a = 0; a < actions.size(); a++) {
                    int[] r = buttonRect(x, w, cardY(i), a, actions.size());
                    if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                        ModNetwork.CHANNEL.sendToServer(
                                new BookActionPacket(entry.table(), q.getId(), actions.get(a).code()));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(delta), maxScroll()));
        return true;
    }

    private String buildContextLine(BookEntry entry) {
        StringBuilder sb = new StringBuilder();
        if (entry.ownerName() != null && !entry.ownerName().isEmpty()) {
            sb.append(entry.ownerName());
        } else {
            sb.append("?");
        }
        BlockPos table = entry.table().pos();
        sb.append(" · ").append(table.getX()).append(' ').append(table.getY()).append(' ').append(table.getZ());
        List<BlockPos> chests = entry.chestPositions();
        if (chests.size() == 1) {
            BlockPos c = chests.get(0);
            sb.append(" → ").append(c.getX()).append(' ').append(c.getY()).append(' ').append(c.getZ());
        } else if (chests.size() > 1) {
            sb.append(" → ×").append(chests.size());
        }
        return sb.toString();
    }
}
