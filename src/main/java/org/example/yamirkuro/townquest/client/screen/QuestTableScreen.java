package org.example.yamirkuro.townquest.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.example.yamirkuro.townquest.block.entity.QuestTableBlockEntity;
import org.example.yamirkuro.townquest.client.QuestUi;
import org.example.yamirkuro.townquest.menu.QuestTableMenu;
import org.example.yamirkuro.townquest.network.ModNetwork;
import org.example.yamirkuro.townquest.network.packet.DeleteQuestPacket;
import org.example.yamirkuro.townquest.network.packet.ExtractArchivePacket;
import org.example.yamirkuro.townquest.network.packet.ForceCompletePacket;
import org.example.yamirkuro.townquest.network.packet.StartBindingPacket;
import org.example.yamirkuro.townquest.quest.Quest;
import org.example.yamirkuro.townquest.quest.QuestParticipant;
import org.example.yamirkuro.townquest.quest.QuestKind;
import org.example.yamirkuro.townquest.quest.QuestStatus;
import org.example.yamirkuro.townquest.quest.QuestType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class QuestTableScreen extends AbstractContainerScreen<QuestTableMenu> {

    /** Вища картка: 2 рядки опису + коротка смуга прогресу. */
    private static final int CARD_H = 54;
    private static final int CARD_GAP = 2;
    private static final int VISIBLE = 2;
    private static final int LIST_TOP = 30;
    private static final int DESC_WIDTH = 200;
    private static final int BAR_W = 48;

    private int scroll = 0;
    private Button createButton;
    private Button bindButton;
    private Button extractButton;

    public QuestTableScreen(QuestTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 196;
    }

    @Override
    protected void init() {
        super.init();
        createButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.townquest.create_quest"),
                        b -> minecraft.setScreen(new CreateQuestScreen(menu.getPos(), this)))
                .bounds(leftPos + 8, topPos + 170, 80, 20)
                .build());

        bindButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.townquest.bind_container"),
                        b -> {
                            ModNetwork.CHANNEL.sendToServer(new StartBindingPacket(menu.getPos()));
                            onClose();
                        })
                .bounds(leftPos + 92, topPos + 170, 80, 20)
                .build());

        extractButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.townquest.extract_archive"),
                        b -> ModNetwork.CHANNEL.sendToServer(new ExtractArchivePacket(menu.getPos())))
                .bounds(leftPos + 176, topPos + 170, 72, 20)
                .build());

        updateButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private void updateButtons() {
        boolean manage = canManage();
        if (createButton != null) createButton.visible = manage;
        if (bindButton != null) bindButton.visible = manage;
        if (extractButton != null) extractButton.visible = manage;
    }

    @Nullable
    private QuestTableBlockEntity table() {
        if (minecraft == null || minecraft.level == null) return null;
        return minecraft.level.getBlockEntity(menu.getPos()) instanceof QuestTableBlockEntity t ? t : null;
    }

    private List<Quest> quests() {
        QuestTableBlockEntity t = table();
        return t == null ? List.of() : t.getQuests();
    }

    private int containerCount() {
        QuestTableBlockEntity t = table();
        return t == null ? 0 : t.getContainers().size();
    }

    private boolean canManage() {
        QuestTableBlockEntity t = table();
        return t != null && minecraft != null && minecraft.player != null
                && t.canManage(minecraft.player.getUUID());
    }

    private int maxScroll() {
        return Math.max(0, quests().size() - VISIBLE);
    }

    private static boolean needsReview(Quest q) {
        boolean kindOk = q.getKind() == QuestKind.MANUAL
                || (q.getKind() == QuestKind.ITEMS && q.isRequireReview());
        if (!kindOk) return false;
        if (q.allowsMultiple()) return q.hasAnyoneInReview();
        return q.getStatus() == QuestStatus.REVIEW;
    }

    private static int participantStatusColor(QuestStatus st) {
        return switch (st) {
            case IN_PROGRESS -> 0xFF55AAFF;
            case REVIEW -> 0xFFFFAA00;
            case DONE -> 0xFF55FF55;
            default -> 0xFF888888;
        };
    }

    private int cardY(int visibleIndex) {
        return topPos + LIST_TOP + visibleIndex * (CARD_H + CARD_GAP);
    }

    @Nullable
    private Quest cardAt(double mouseX, double mouseY) {
        List<Quest> quests = quests();
        int x = leftPos + 8;
        int w = imageWidth - 16;
        for (int i = 0; i < VISIBLE; i++) {
            int idx = scroll + i;
            if (idx >= quests.size()) break;
            int cy = cardY(i);
            if (mouseX >= x && mouseX < x + w && mouseY >= cy && mouseY < cy + CARD_H) {
                return quests.get(idx);
            }
        }
        return null;
    }

    private void drawShortProgress(GuiGraphics g, Quest q, int barX, int barY, int color) {
        int percent = q.getStatus() == QuestStatus.DONE ? 100 : Math.min(100, q.percent());
        g.fill(barX, barY, barX + BAR_W, barY + 5, 0xFF000000);
        g.fill(barX, barY, barX + Math.max(0, BAR_W * percent / 100), barY + 5, color);
        g.drawString(font, q.getDelivered() + "/" + q.getRequired(),
                barX + BAR_W + 4, barY - 2, color, false);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEE141414);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF6B6B6B);

        List<Quest> quests = quests();
        if (quests.isEmpty()) {
            Component empty = Component.translatable("gui.townquest.no_quests");
            g.drawString(font, empty, leftPos + (imageWidth - font.width(empty)) / 2,
                    topPos + LIST_TOP + 50, 0x888888, false);
            return;
        }

        boolean manage = canManage();
        int x = leftPos + 8;
        int w = imageWidth - 16;

        for (int i = 0; i < VISIBLE; i++) {
            int idx = scroll + i;
            if (idx >= quests.size()) break;

            Quest q = quests.get(idx);
            int cy = cardY(i);
            boolean done = q.getStatus() == QuestStatus.DONE;
            boolean review = manage && needsReview(q);
            int rightReserve = manage ? 30 : 18;

            g.fill(x, cy, x + w, cy + CARD_H, done ? 0xFF222222 : 0xFF2A2A2A);
            g.renderOutline(x, cy, w, CARD_H, review ? 0xFFFFAA00 : (done ? 0xFF3F3F3F : 0xFF555555));

            g.renderItem(QuestUi.icon(q), x + 6, cy + 18);

            g.drawString(font, font.plainSubstrByWidth(q.getTitle(), w - 30 - rightReserve),
                    x + 30, cy + 4, done ? 0x888888 : 0xFFFFFF, false);
            g.drawString(font, QuestUi.subtitle(q), x + 30, cy + 14, QuestUi.subtitleColor(q), false);

            int textTop = cy + 24;
            if (review) {
                // рядок 1: статус учасників або «клік»
                if (q.allowsMultiple()) {
                    int ir = q.countInReview();
                    int ip = 0, idn = 0;
                    for (QuestParticipant p : q.getParticipants()) {
                        if (p.getStatus() == QuestStatus.IN_PROGRESS) ip++;
                        if (p.getStatus() == QuestStatus.DONE) idn++;
                    }
                    String sum = ir + "✉ " + ip + "… " + idn + "✓";
                    g.drawString(font, sum, x + 30, textTop, 0xFFAA00, false);
                } else {
                    g.drawString(font, Component.translatable("gui.townquest.review.click_hint"),
                            x + 30, textTop, 0xFFAA00, false);
                }
                // рядок 2: коротка підказка (без наїзду на смугу)
                g.drawString(font, Component.translatable("gui.townquest.review.click_hint"),
                        x + 30, textTop + 10, 0xFFCC66, false);
                // смуга прогресу окремо; Shift-підказка справа від чисел
                drawShortProgress(g, q, x + 30, cy + 44, 0xFFE0B03D);
                String force = Component.translatable("gui.townquest.force_complete_short").getString();
                g.drawString(font, force, x + 30 + BAR_W + 36, cy + 42, 0x88FF88, false);
            } else {
                // опис з переносом (до 2 рядків)
                List<FormattedCharSequence> lines = font.split(
                        Component.literal(q.getDescription()), DESC_WIDTH);
                int lineY = textTop;
                for (int li = 0; li < Math.min(2, lines.size()); li++) {
                    g.drawString(font, lines.get(li), x + 30, lineY, 0xAAAAAA, false);
                    lineY += 10;
                }
                int barColor = done ? 0xFFE0B03D : 0xFF3DAA3D;
                drawShortProgress(g, q, x + 30, cy + 44, barColor);
            }

            // голови учасників (мульти) або один takenBy
            int headY = cy + 3;
            int headSize = 12;
            if (q.allowsMultiple() && !q.getParticipants().isEmpty()) {
                int maxHeads = 6;
                int n = Math.min(maxHeads, q.getParticipants().size());
                int startX = x + w - 16 - n * (headSize + 3);
                headY = cy + 2;
                for (int hi = 0; hi < n; hi++) {
                    QuestParticipant p = q.getParticipants().get(hi);
                    int hx = startX + hi * (headSize + 3);
                    int col = participantStatusColor(p.getStatus());
                    g.fill(hx - 1, headY - 1, hx + headSize + 1, headY + headSize + 1, col);
                    QuestUi.drawHead(g, p.getId(), hx, headY, headSize);
                }
                if (q.getParticipants().size() > maxHeads) {
                    g.drawString(font, "+" + (q.getParticipants().size() - maxHeads),
                            startX - 14, headY + 2, 0xAAAAAA, false);
                }
            } else if (q.getTakenBy() != null) {
                QuestUi.drawHead(g, q.getTakenBy(), x + w - rightReserve, headY, headSize);
            }
            if (manage) {
                g.drawString(font, "X", x + w - 11, cy + 4, 0xFF5555, false);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 8, 0xFFFFFF, false);

        Component counts = Component.translatable("gui.townquest.header_counts",
                quests().size(), containerCount());
        g.drawString(font, counts, imageWidth - 8 - font.width(counts), 8, 0xAAAAAA, false);

        int reviewCount = 0;
        for (Quest qq : quests()) {
            if (qq.getKind() == QuestKind.MANUAL || (qq.getKind() == QuestKind.ITEMS && qq.isRequireReview())) {
                reviewCount += qq.countInReview();
            }
        }
        if (reviewCount > 0) {
            g.drawString(font, Component.translatable("gui.townquest.header_review", reviewCount),
                    8, 18, 0xFFAA00, false);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        super.renderTooltip(g, mouseX, mouseY);
        Quest hovered = cardAt(mouseX, mouseY);
        if (hovered != null) {
            QuestUi.renderQuestTooltip(g, font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(delta), maxScroll()));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && canManage()) {
            List<Quest> quests = quests();
            int x = leftPos + 8;
            int w = imageWidth - 16;
            for (int i = 0; i < VISIBLE; i++) {
                int idx = scroll + i;
                if (idx >= quests.size()) break;
                Quest q = quests.get(idx);
                int cy = cardY(i);

                // видалити (X)
                int bx = x + w - 13;
                if (mouseX >= bx && mouseX < bx + 10 && mouseY >= cy + 3 && mouseY < cy + 13) {
                    ModNetwork.CHANNEL.sendToServer(new DeleteQuestPacket(menu.getPos(), q.getId()));
                    return true;
                }

                // Shift+клік по картці = примусово DONE (усі учасники)
                if (net.minecraft.client.gui.screens.Screen.hasShiftDown()
                        && q.getStatus() != QuestStatus.DONE
                        && mouseX >= x && mouseX < x + w && mouseY >= cy && mouseY < cy + CARD_H) {
                    ModNetwork.CHANNEL.sendToServer(new ForceCompletePacket(menu.getPos(), q.getId()));
                    return true;
                }

                // клік по голові
                if (q.allowsMultiple() && !q.getParticipants().isEmpty()) {
                    int headSize = 12;
                    int maxHeads = 6;
                    int n = Math.min(maxHeads, q.getParticipants().size());
                    int startX = x + w - 16 - n * (headSize + 3);
                    int headY = cy + 2;
                    for (int hi = 0; hi < n; hi++) {
                        QuestParticipant p = q.getParticipants().get(hi);
                        int hx = startX + hi * (headSize + 3);
                        // ширша зона кліку
                        if (mouseX >= hx - 2 && mouseX <= hx + headSize + 2
                                && mouseY >= headY - 2 && mouseY <= headY + headSize + 2) {
                            if (p.getStatus() == QuestStatus.REVIEW) {
                                minecraft.setScreen(new ReviewQuestScreen(
                                        this, menu.getPos(), q.getId(), p.getId()));
                                return true;
                            }
                            // не REVIEW — все одно можна клікнути картку нижче
                        }
                    }
                }

                // клік по картці / кнопці «Перевірити»
                if (needsReview(q) && mouseX >= x && mouseX < x + w && mouseY >= cy && mouseY < cy + CARD_H) {
                    UUID pid = null;
                    if (q.allowsMultiple()) {
                        for (QuestParticipant p : q.getParticipants()) {
                            if (p.getStatus() == QuestStatus.REVIEW) {
                                pid = p.getId();
                                break;
                            }
                        }
                        if (pid == null) {
                            // клієнт ще не підтягнув participants — fallback
                            pid = q.getTakenBy();
                        }
                    }
                    minecraft.setScreen(new ReviewQuestScreen(this, menu.getPos(), q.getId(), pid));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
