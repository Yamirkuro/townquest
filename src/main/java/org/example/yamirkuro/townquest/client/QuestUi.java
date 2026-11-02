package org.example.yamirkuro.townquest.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.example.yamirkuro.townquest.quest.BookEntry;
import org.example.yamirkuro.townquest.data.BoundContainer;
import org.example.yamirkuro.townquest.quest.Quest;
import org.example.yamirkuro.townquest.quest.QuestKind;
import org.example.yamirkuro.townquest.quest.QuestLocation;
import org.example.yamirkuro.townquest.quest.QuestStatus;
import org.example.yamirkuro.townquest.quest.QuestType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Спільні клієнтські допоміжні методи для карток квестів. */
public final class QuestUi {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());

    private QuestUi() {}

    public static String formatTime(long millis) {
        return TIME.format(Instant.ofEpochMilli(millis));
    }

    // ---------- голова виконавця ----------

    /** Скін гравця (якщо він зараз у мережі), інакше стандартний за UUID. */
    public static ResourceLocation skinOf(UUID uuid) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            PlayerInfo info = connection.getPlayerInfo(uuid);
            if (info != null) {
                return info.getSkinLocation();
            }
        }
        return DefaultPlayerSkin.getDefaultSkin(uuid);
    }

    /** Малює голову гравця (обличчя + шар капелюха). */
    public static void drawHead(GuiGraphics g, UUID uuid, int x, int y, int size) {
        ResourceLocation skin = skinOf(uuid);
        RenderSystem.enableBlend();
        g.blit(skin, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
        g.blit(skin, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
    }

    // ---------- вигляд картки ----------

    public static ItemStack icon(Quest q) {
        if (q.getKind() == QuestKind.MANUAL) {
            return new ItemStack(Items.WRITABLE_BOOK);
        }
        Item item = ForgeRegistries.ITEMS.getValue(q.getItem());
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** Другий рядок картки: назва предмета або позначка ручної перевірки. */
    public static Component subtitle(Quest q) {
        if (q.getKind() == QuestKind.MANUAL) {
            return Component.translatable("gui.townquest.kind.manual");
        }
        ItemStack stack = icon(q);
        return stack.isEmpty() ? Component.empty() : stack.getHoverName();
    }

    public static int subtitleColor(Quest q) {
        return q.getKind() == QuestKind.MANUAL ? 0x9FE0FF : 0xFFD27F;
    }

    public static Component statusLabel(Quest q) {
        return Component.translatable("gui.townquest.status." + q.getStatus().name().toLowerCase(Locale.ROOT));
    }

    public static int statusColor(Quest q) {
        return switch (q.getStatus()) {
            case OPEN -> 0xAAAAAA;
            case IN_PROGRESS -> 0x7FD4FF;
            case REVIEW -> 0xFFAA00;
            case DONE -> 0x55FF55;
        };
    }

    /** Рядок прогресу: смуга з числами для квестів на предмети або статус для ручних. */
    public static void drawProgressRow(GuiGraphics g, Font font, Quest q, int barX, int barY, int barW) {
        if (q.getKind() == QuestKind.MANUAL) {
            g.drawString(font, statusLabel(q), barX, barY - 2, statusColor(q), false);
            return;
        }
        boolean done = q.getStatus() == QuestStatus.DONE;
        int percent = done ? 100 : q.percent();
        int barColor = percent >= 100 ? 0xFFE0B03D : 0xFF3DAA3D;
        g.fill(barX, barY, barX + barW, barY + 5, 0xFF000000);
        g.fill(barX, barY, barX + Math.max(0, barW * percent / 100), barY + 5, barColor);
        if (done) {
            g.drawString(font, statusLabel(q), barX + barW + 4, barY - 2, statusColor(q), false);
        } else {
            g.drawString(font, q.getDelivered() + "/" + q.getRequired(),
                    barX + barW + 4, barY - 2, 0xFFFFFF, false);
        }
    }

    // ---------- місце роботи ----------

    private static String fmt(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    public static List<Component> locationLines(Quest q) {
        List<Component> lines = new ArrayList<>();
        QuestLocation loc = q.getLocation();
        if (loc == null) return lines;

        if (loc.isArea()) {
            lines.add(Component.translatable("gui.townquest.tip.location_area",
                    fmt(loc.min()), fmt(loc.max()), loc.volume()));
        } else {
            lines.add(Component.translatable("gui.townquest.tip.location_point", fmt(loc.min())));
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            if (mc.level.dimension().equals(loc.dimension())) {
                lines.add(Component.translatable("gui.townquest.tip.distance",
                        (int) loc.distanceTo(mc.player.position())));
            } else {
                lines.add(Component.translatable("gui.townquest.tip.other_dim",
                        loc.dimension().location().toString()));
            }
        }
        return lines;
    }

    // ---------- підказка ----------

    public static List<Component> tooltip(Quest q) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(q.getTitle()).withStyle(ChatFormatting.WHITE));
        if (!q.getDescription().isEmpty()) {
            lines.add(Component.literal(q.getDescription()).withStyle(ChatFormatting.GRAY));
        }

        lines.add(Component.translatable("gui.townquest.tip.status", statusLabel(q))
                .withStyle(style -> style.withColor(statusColor(q))));

        if (q.getType() == QuestType.PERSONAL) {
            lines.add(Component.translatable("gui.townquest.tip.personal", q.getAssigneeName())
                    .withStyle(ChatFormatting.AQUA));
        } else if (q.getType() == QuestType.GROUP) {
            String members = String.join(", ", q.getGroupMemberNames());
            lines.add(Component.translatable("gui.townquest.tip.group", members)
                    .withStyle(ChatFormatting.AQUA));
        } else {
            lines.add(Component.translatable("gui.townquest.tip.global").withStyle(ChatFormatting.DARK_AQUA));
        }

        if (q.getKind() == QuestKind.ITEMS) {
            lines.add(Component.translatable("gui.townquest.tip.delivered", q.getDelivered(), q.getRequired()));
            lines.add(Component.translatable("gui.townquest.tip.remaining",
                    Math.max(0, q.getRequired() - q.getDelivered())));
            if (q.isRequireReview()) {
                lines.add(Component.translatable("gui.townquest.tip.items_manual_review")
                        .withStyle(ChatFormatting.GOLD));
            } else {
                lines.add(Component.translatable("gui.townquest.tip.items_auto")
                        .withStyle(ChatFormatting.DARK_GREEN));
            }
            if (q.isTaken() && q.getStatus() != QuestStatus.DONE) {
                lines.add(Component.translatable("gui.townquest.tip.in_inventory", q.getInInventory())
                        .withStyle(ChatFormatting.YELLOW));
            }
        } else {
            lines.add(Component.translatable("gui.townquest.tip.kind_manual").withStyle(ChatFormatting.DARK_GRAY));
        }

        for (Component line : locationLines(q)) {
            lines.add(line.copy().withStyle(ChatFormatting.GOLD));
        }

        if (q.allowsMultiple() && !q.getParticipants().isEmpty()) {
            lines.add(Component.translatable("gui.townquest.tip.taken_by", q.participantNamesJoined())
                    .withStyle(ChatFormatting.GREEN));
            lines.add(Component.literal("(" + q.getParticipants().size() + ")")
                    .withStyle(ChatFormatting.DARK_GREEN));
        } else if (q.isTaken()) {
            lines.add(Component.translatable("gui.townquest.tip.taken_by", q.getTakenByName())
                    .withStyle(ChatFormatting.GREEN));
            lines.add(Component.translatable("gui.townquest.tip.taken_at", formatTime(q.getTakenAt()))
                    .withStyle(ChatFormatting.GREEN));
        } else if (q.getStatus() == QuestStatus.OPEN) {
            lines.add(Component.translatable("gui.townquest.tip.not_taken").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    public static List<Component> tooltip(BookEntry entry) {
        Quest q = entry.quest();
        List<Component> lines = tooltip(q);

        BoundContainer table = entry.table();
        String owner = entry.ownerName();
        if (owner != null && !owner.isEmpty()) {
            lines.add(Component.translatable("gui.townquest.tip.issued_by", owner)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        lines.add(Component.translatable("gui.townquest.tip.table_pos", fmt(table.pos()))
                .withStyle(ChatFormatting.GRAY));

        List<BlockPos> chests = entry.chestPositions();
        if (chests.isEmpty()) {
            if (q.getKind() == QuestKind.MANUAL) {
                lines.add(Component.translatable("gui.townquest.tip.turn_in_owner")
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                lines.add(Component.translatable("gui.townquest.tip.no_chest")
                        .withStyle(ChatFormatting.RED));
            }
        } else if (chests.size() == 1) {
            lines.add(Component.translatable("gui.townquest.tip.chest_pos", fmt(chests.get(0)))
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            lines.add(Component.translatable("gui.townquest.tip.chest_count", chests.size())
                    .withStyle(ChatFormatting.YELLOW));
            for (BlockPos p : chests) {
                lines.add(Component.literal("  • " + fmt(p)).withStyle(ChatFormatting.YELLOW));
            }
        }
        return lines;
    }

    public static void renderQuestTooltip(GuiGraphics g, Font font, Quest quest, int mouseX, int mouseY) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : tooltip(quest)) {
            wrapped.addAll(font.split(line, 220));
        }
        g.renderTooltip(font, wrapped, mouseX, mouseY);
    }

    public static void renderQuestTooltip(GuiGraphics g, Font font, BookEntry entry, int mouseX, int mouseY) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : tooltip(entry)) {
            wrapped.addAll(font.split(line, 220));
        }
        g.renderTooltip(font, wrapped, mouseX, mouseY);
    }
}
