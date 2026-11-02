package org.example.yamirkuro.townquest.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.example.yamirkuro.townquest.network.ModNetwork;
import org.example.yamirkuro.townquest.item.QuestGlobeItem;
import org.example.yamirkuro.townquest.network.packet.CreateQuestPacket;
import org.example.yamirkuro.townquest.quest.QuestLocation;
import org.example.yamirkuro.townquest.quest.Quest;
import org.example.yamirkuro.townquest.quest.QuestKind;
import org.example.yamirkuro.townquest.quest.QuestType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Форма створення квесту з двох сторінок: 1) основне, 2) предмет або місце роботи. */
public class CreateQuestScreen extends Screen {

    private enum LocMode {
        NONE, POINT, AREA;

        LocMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private record Label(Component text, int y) {}

    private final BlockPos pos;
    private final Screen parent;

    // усі значення зберігаємо в полях, щоб вони не губилися при зміні сторінки, виборі предмета чи розміру вікна
    private int page = 1;
    private String titleValue = "";
    private String descValue = "";
    private QuestKind kindValue = QuestKind.ITEMS;
    private boolean requireReviewValue = false;
    private QuestType typeValue = QuestType.GLOBAL;
    private String playerValue = "";
    private String itemValue = "minecraft:iron_ingot";
    private String amountValue = "10";
    private LocMode locMode = LocMode.NONE;
    private final String[] cornerA = {"", "", ""};
    private final String[] cornerB = {"", "", ""};

    private Component error = null;
    private final List<Label> labels = new ArrayList<>();
    private int itemIconY = Integer.MIN_VALUE;

    public CreateQuestScreen(BlockPos pos, Screen parent) {
        super(Component.translatable("gui.townquest.create_quest"));
        this.pos = pos;
        this.parent = parent;
    }

    private int x() { return width / 2 - 100; }
    private int y0() { return height / 2 - 95; }
    private int row(int n) { return y0() + 10 + n * 32; }

    // ---------- побудова ----------

    @Override
    protected void init() {
        labels.clear();
        itemIconY = Integer.MIN_VALUE;
        if (page == 1) {
            initPage1();
        } else {
            initPage2();
        }
    }

    private void initPage1() {
        int x = x();

        labels.add(new Label(Component.translatable("gui.townquest.field.title"), row(0) - 10));
        EditBox titleBox = addRenderableWidget(new EditBox(font, x, row(0), 200, 20,
                Component.translatable("gui.townquest.field.title")));
        titleBox.setMaxLength(Quest.MAX_TITLE);
        titleBox.setValue(titleValue);
        titleBox.setResponder(s -> titleValue = s);

        labels.add(new Label(Component.translatable("gui.townquest.field.description"), row(1) - 10));
        EditBox descBox = addRenderableWidget(new EditBox(font, x, row(1), 200, 20,
                Component.translatable("gui.townquest.field.description")));
        descBox.setMaxLength(Quest.MAX_DESC);
        descBox.setValue(descValue);
        descBox.setResponder(s -> descValue = s);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.townquest.btn.kind",
                                Component.translatable("gui.townquest.kind." + kindValue.name().toLowerCase(Locale.ROOT))),
                        b -> {
                            kindValue = kindValue.next();
                            rebuildWidgets();
                        })
                .bounds(x, row(2), 200, 20).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.townquest.btn.scope",
                                Component.translatable("gui.townquest.type." + typeValue.name().toLowerCase(Locale.ROOT))),
                        b -> {
                            typeValue = typeValue.next();
                            rebuildWidgets();
                        })
                .bounds(x, row(3), 200, 20).build());

        if (typeValue == QuestType.PERSONAL || typeValue == QuestType.GROUP) {
            Component fieldLabel = Component.translatable(
                    typeValue == QuestType.GROUP ? "gui.townquest.field.group" : "gui.townquest.field.player");
            labels.add(new Label(fieldLabel, row(4) - 10));
            EditBox playerBox = addRenderableWidget(new EditBox(font, x, row(4), 200, 20, fieldLabel));
            playerBox.setMaxLength(typeValue == QuestType.GROUP ? 200 : 32);
            playerBox.setValue(playerValue);
            playerBox.setResponder(s -> playerValue = s);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.townquest.next"), b -> goToPage2())
                .bounds(x, y0() + 170, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.townquest.cancel"), b -> onClose())
                .bounds(x + 102, y0() + 170, 98, 20).build());

        setInitialFocus(titleBox);
    }

    private void initPage2() {
        int x = x();
        int r = 0;

        if (kindValue == QuestKind.ITEMS) {
            labels.add(new Label(Component.translatable("gui.townquest.field.item"), row(r) - 10));
            itemIconY = row(r) - 17;
            EditBox itemBox = addRenderableWidget(new EditBox(font, x, row(r), 172, 20,
                    Component.translatable("gui.townquest.field.item")));
            itemBox.setMaxLength(100);
            itemBox.setValue(itemValue);
            itemBox.setResponder(s -> itemValue = s);

            addRenderableWidget(Button.builder(Component.literal("..."),
                            b -> minecraft.setScreen(new ItemPickerScreen(this, id -> itemValue = id.toString())))
                    .bounds(x + 176, row(r), 24, 20).build());
            r++;

            labels.add(new Label(Component.translatable("gui.townquest.field.amount"), row(r) - 10));
            EditBox amountBox = addRenderableWidget(new EditBox(font, x, row(r), 200, 20,
                    Component.translatable("gui.townquest.field.amount")));
            amountBox.setMaxLength(6);
            amountBox.setValue(amountValue);
            amountBox.setResponder(s -> amountValue = s);
            r++;

            addRenderableWidget(Button.builder(
                            Component.translatable("gui.townquest.btn.review_mode",
                                    Component.translatable(requireReviewValue
                                            ? "gui.townquest.review_mode.manual"
                                            : "gui.townquest.review_mode.auto")),
                            b -> {
                                requireReviewValue = !requireReviewValue;
                                rebuildWidgets();
                            })
                    .bounds(x, row(r), 200, 20).build());
            r++;
        }

        labels.add(new Label(Component.translatable("gui.townquest.field.location"), row(r) - 10));
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.townquest.btn.location",
                                Component.translatable("gui.townquest.loc." + locMode.name().toLowerCase(Locale.ROOT))),
                        b -> {
                            locMode = locMode.next();
                            rebuildWidgets();
                        })
                .bounds(x, row(r), 200, 20).build());
        r++;

        if (locMode != LocMode.NONE) {
            Component labelA = Component.translatable(locMode == LocMode.AREA
                    ? "gui.townquest.field.corner_a" : "gui.townquest.field.point");
            coordRow(r++, labelA, cornerA);
            if (locMode == LocMode.AREA) {
                coordRow(r++, Component.translatable("gui.townquest.field.corner_b"), cornerB);
            }
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.townquest.from_globe"),
                            b -> applyFromGlobe())
                    .bounds(x, row(r), 200, 20).build());
            r++;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.townquest.back"), b -> {
                    page = 1;
                    error = null;
                    rebuildWidgets();
                })
                .bounds(x, y0() + 170, 64, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.townquest.create"), b -> submit())
                .bounds(x + 68, y0() + 170, 64, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.townquest.cancel"), b -> onClose())
                .bounds(x + 136, y0() + 170, 64, 20).build());
    }

    /** Рядок із трьох полів X Y Z і кнопки «Тут», що підставляє позицію гравця. */
    private void coordRow(int rowIndex, Component label, String[] values) {
        int x = x();
        int y = row(rowIndex);
        labels.add(new Label(label, y - 10));

        for (int i = 0; i < 3; i++) {
            final int index = i;
            EditBox box = addRenderableWidget(new EditBox(font, x + i * 53, y, 50, 20, Component.literal("xyz")));
            box.setMaxLength(8);
            box.setValue(values[i]);
            box.setResponder(s -> values[index] = s);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.townquest.here"), b -> {
                    if (minecraft != null && minecraft.player != null) {
                        BlockPos p = minecraft.player.blockPosition();
                        values[0] = String.valueOf(p.getX());
                        values[1] = String.valueOf(p.getY());
                        values[2] = String.valueOf(p.getZ());
                        rebuildWidgets();
                    }
                })
                .bounds(x + 160, y, 40, 20).build());
    }

    // ---------- дії ----------

    private void goToPage2() {
        if (titleValue.trim().isEmpty()) {
            error = Component.translatable("gui.townquest.err.title");
            return;
        }
        if ((typeValue == QuestType.PERSONAL || typeValue == QuestType.GROUP)
                && playerValue.trim().isEmpty()) {
            error = Component.translatable(
                    typeValue == QuestType.GROUP ? "gui.townquest.err.group" : "gui.townquest.err.player");
            return;
        }
        error = null;
        page = 2;
        rebuildWidgets();
    }

    private static BlockPos parsePos(String[] v) {
        return new BlockPos(
                Integer.parseInt(v[0].trim()), Integer.parseInt(v[1].trim()), Integer.parseInt(v[2].trim()));
    }


    private void applyFromGlobe() {
        if (minecraft == null || minecraft.player == null) return;
        var stack = QuestGlobeItem.findInInventory(minecraft.player);
        if (stack == null) {
            error = Component.translatable("message.townquest.globe_empty");
            return;
        }
        var loc = QuestGlobeItem.readLocation(stack, minecraft.level != null
                ? minecraft.level.dimension() : net.minecraft.world.level.Level.OVERWORLD);
        if (loc == null) {
            error = Component.translatable("message.townquest.globe_empty");
            return;
        }
        cornerA[0] = Integer.toString(loc.min().getX());
        cornerA[1] = Integer.toString(loc.min().getY());
        cornerA[2] = Integer.toString(loc.min().getZ());
        if (loc.isArea()) {
            locMode = LocMode.AREA;
            cornerB[0] = Integer.toString(loc.max().getX());
            cornerB[1] = Integer.toString(loc.max().getY());
            cornerB[2] = Integer.toString(loc.max().getZ());
        } else {
            locMode = LocMode.POINT;
            cornerB[0] = cornerB[1] = cornerB[2] = "";
        }
        error = null;
        rebuildWidgets();
    }

    private void submit() {
        int amount = 0;
        String item = "";
        if (kindValue == QuestKind.ITEMS) {
            try {
                amount = Integer.parseInt(amountValue.trim());
            } catch (NumberFormatException e) {
                error = Component.translatable("gui.townquest.err.amount");
                return;
            }
            if (amount < 1 || amount > Quest.MAX_AMOUNT) {
                error = Component.translatable("gui.townquest.err.amount");
                return;
            }
            item = itemValue.trim();
            ResourceLocation id = ResourceLocation.tryParse(item);
            if (id == null || !ForgeRegistries.ITEMS.containsKey(id) || ForgeRegistries.ITEMS.getValue(id) == Items.AIR) {
                error = Component.translatable("gui.townquest.err.item");
                return;
            }
        }

        QuestLocation location = null;
        if (locMode != LocMode.NONE && minecraft != null && minecraft.level != null) {
            try {
                BlockPos a = parsePos(cornerA);
                BlockPos b = locMode == LocMode.AREA ? parsePos(cornerB) : a;
                location = QuestLocation.of(minecraft.level.dimension(), a, b);
            } catch (NumberFormatException e) {
                error = Component.translatable("gui.townquest.err.coords");
                return;
            }
            if (!location.fitsLimit()) {
                error = Component.translatable("gui.townquest.err.area_size", QuestLocation.MAX_SIDE);
                return;
            }
        }

        ModNetwork.CHANNEL.sendToServer(new CreateQuestPacket(
                pos, titleValue.trim(), descValue.trim(), item, amount,
                typeValue, playerValue.trim(), kindValue,
                kindValue == QuestKind.ITEMS && requireReviewValue, location));
        onClose();
    }

    // ---------- малювання ----------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = x();

        for (Label label : labels) {
            g.drawString(font, label.text(), x, label.y(), 0xA0A0A0, false);
        }

        // іконка вибраного предмета праворуч від підпису
        if (page == 2 && kindValue == QuestKind.ITEMS && itemIconY != Integer.MIN_VALUE) {
            ResourceLocation id = ResourceLocation.tryParse(itemValue.trim());
            if (id != null && ForgeRegistries.ITEMS.containsKey(id)) {
                Item item = ForgeRegistries.ITEMS.getValue(id);
                if (item != null && item != Items.AIR) {
                    g.renderItem(new ItemStack(item), x + 184, itemIconY);
                }
            }
        }

        if (error != null) {
            g.drawCenteredString(font, error, width / 2, y0() + 160, 0xFF5555);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
