package org.example.yamirkuro.townquest.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.example.yamirkuro.townquest.block.entity.QuestTableBlockEntity;
import org.example.yamirkuro.townquest.client.QuestUi;
import org.example.yamirkuro.townquest.network.ModNetwork;
import org.example.yamirkuro.townquest.network.packet.ReviewQuestPacket;
import org.example.yamirkuro.townquest.quest.Quest;
import org.example.yamirkuro.townquest.quest.QuestParticipant;
import org.example.yamirkuro.townquest.quest.QuestStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Перевірка: весь квест або конкретний учасник (мульти). */
public class ReviewQuestScreen extends Screen {

    private static final int W = 220;
    private static final int H = 170;

    private final Screen parent;
    private final BlockPos tablePos;
    private final UUID questId;
    @Nullable
    private final UUID participantId;

    public ReviewQuestScreen(Screen parent, BlockPos tablePos, UUID questId) {
        this(parent, tablePos, questId, null);
    }

    public ReviewQuestScreen(Screen parent, BlockPos tablePos, UUID questId, @Nullable UUID participantId) {
        super(Component.translatable("gui.townquest.review.title"));
        this.parent = parent;
        this.tablePos = tablePos;
        this.questId = questId;
        this.participantId = participantId;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Nullable
    private Quest quest() {
        if (minecraft == null || minecraft.level == null) return null;
        if (minecraft.level.getBlockEntity(tablePos) instanceof QuestTableBlockEntity table) {
            return table.findQuest(questId);
        }
        return null;
    }

    @Override
    protected void init() {
        int l = left();
        int y = top() + H - 28;

        addRenderableWidget(Button.builder(Component.translatable("gui.townquest.review.approve"),
                        b -> send(true))
                .bounds(l + 8, y, 66, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.townquest.review.reject"),
                        b -> send(false))
                .bounds(l + 77, y, 66, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.townquest.review.back"),
                        b -> onClose())
                .bounds(l + 146, y, 66, 20).build());
    }

    private void send(boolean approve) {
        ModNetwork.CHANNEL.sendToServer(new ReviewQuestPacket(tablePos, questId, approve, participantId));
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        Quest q = quest();
        if (q == null) {
            onClose();
            return;
        }
        if (participantId != null) {
            QuestParticipant p = q.getParticipant(participantId);
            if (p == null || p.getStatus() != QuestStatus.REVIEW) {
                onClose();
            }
        } else if (q.getStatus() != QuestStatus.REVIEW) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int l = left();
        int t = top();
        g.fill(l, t, l + W, t + H, 0xEE141414);
        g.renderOutline(l, t, W, H, 0xFFFFAA00);

        g.drawString(font, title, l + 8, t + 8, 0xFFFFFF, false);

        Quest q = quest();
        if (q != null) {
            g.drawString(font, font.plainSubstrByWidth(q.getTitle(), W - 16), l + 8, t + 28, 0xFFD27F, false);
            String who;
            if (participantId != null) {
                QuestParticipant p = q.getParticipant(participantId);
                who = p != null ? p.getName() : "?";
            } else {
                who = q.getTakenByName();
            }
            g.drawString(font, Component.translatable("gui.townquest.review.performer", who),
                    l + 8, t + 42, 0xAAAAAA, false);

            List<FormattedCharSequence> lines = font.split(
                    Component.literal(q.getDescription().isEmpty() ? "—" : q.getDescription()), W - 16);
            int y = t + 58;
            for (int i = 0; i < Math.min(5, lines.size()); i++) {
                g.drawString(font, lines.get(i), l + 8, y, 0xCCCCCC, false);
                y += 10;
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
