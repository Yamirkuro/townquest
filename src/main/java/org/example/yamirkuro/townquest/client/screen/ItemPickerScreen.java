package org.example.yamirkuro.townquest.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Сітка всіх предметів з пошуком за назвою або ID (враховує й предмети з інших модів). */
public class ItemPickerScreen extends Screen {

    private static final int COLS = 9;
    private static final int ROWS = 5;
    private static final int SLOT = 18;
    private static final int PANEL_W = COLS * SLOT + 24;
    private static final int PANEL_H = 176;

    private record Entry(ItemStack stack, ResourceLocation id, String nameLower) {}

    private final Screen parent;
    private final Consumer<ResourceLocation> onPick;
    private final List<Entry> all = new ArrayList<>();
    private List<Entry> filtered = new ArrayList<>();

    private EditBox searchBox;
    private String query = "";
    private int scroll = 0;

    public ItemPickerScreen(Screen parent, Consumer<ResourceLocation> onPick) {
        super(Component.translatable("gui.townquest.pick_item"));
        this.parent = parent;
        this.onPick = onPick;

        for (Item item : ForgeRegistries.ITEMS) {
            if (item == Items.AIR) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null) continue;
            ItemStack stack = new ItemStack(item);
            all.add(new Entry(stack, id, stack.getHoverName().getString().toLowerCase(Locale.ROOT)));
        }
        all.sort(Comparator.comparing(e -> e.id().toString()));
    }

    // ---------- геометрія ----------

    private int panelLeft() { return (width - PANEL_W) / 2; }
    private int panelTop() { return (height - PANEL_H) / 2; }
    private int gridLeft() { return panelLeft() + 8; }
    private int gridTop() { return panelTop() + 44; }

    private int totalRows() { return (filtered.size() + COLS - 1) / COLS; }
    private int maxScroll() { return Math.max(0, totalRows() - ROWS); }

    private int indexAt(double mx, double my) {
        int gx = gridLeft();
        int gy = gridTop();
        if (mx < gx || my < gy || mx >= gx + COLS * SLOT || my >= gy + ROWS * SLOT) return -1;
        int col = (int) ((mx - gx) / SLOT);
        int row = (int) ((my - gy) / SLOT);
        int idx = (scroll + row) * COLS + col;
        return idx < filtered.size() ? idx : -1;
    }

    // ---------- фільтр ----------

    private void applyFilter() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Entry> result = new ArrayList<>();
        for (Entry e : all) {
            if (q.isEmpty() || e.nameLower().contains(q) || e.id().toString().contains(q)) {
                result.add(e);
            }
        }
        filtered = result;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    @Override
    protected void init() {
        searchBox = addRenderableWidget(new EditBox(font, gridLeft(), panelTop() + 20, COLS * SLOT, 18,
                Component.translatable("gui.townquest.search")));
        searchBox.setMaxLength(64);
        searchBox.setValue(query);
        searchBox.setResponder(s -> {
            query = s;
            scroll = 0;
            applyFilter();
        });

        addRenderableWidget(Button.builder(Component.translatable("gui.townquest.cancel"), b -> onClose())
                .bounds(panelLeft() + 8, panelTop() + 150, PANEL_W - 16, 20)
                .build());

        setInitialFocus(searchBox);
        applyFilter();
    }

    // ---------- малювання ----------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int pl = panelLeft();
        int pt = panelTop();
        g.fill(pl, pt, pl + PANEL_W, pt + PANEL_H, 0xEE141414);
        g.renderOutline(pl, pt, PANEL_W, PANEL_H, 0xFF6B6B6B);
        g.drawString(font, title, pl + 8, pt + 7, 0xFFFFFF, false);

        int gx = gridLeft();
        int gy = gridTop();
        int hovered = indexAt(mouseX, mouseY);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (scroll + row) * COLS + col;
                int sx = gx + col * SLOT;
                int sy = gy + row * SLOT;
                g.fill(sx, sy, sx + SLOT - 1, sy + SLOT - 1, 0xFF3A3A3A);
                if (idx < filtered.size()) {
                    g.renderItem(filtered.get(idx).stack(), sx + 1, sy + 1);
                    if (idx == hovered) {
                        g.fill(sx, sy, sx + SLOT - 1, sy + SLOT - 1, 0x80FFFFFF);
                    }
                }
            }
        }

        // смуга прокрутки
        if (maxScroll() > 0) {
            int trackX = gx + COLS * SLOT + 3;
            int trackH = ROWS * SLOT;
            int thumbH = Math.max(10, trackH * ROWS / totalRows());
            int thumbY = gy + (trackH - thumbH) * scroll / maxScroll();
            g.fill(trackX, gy, trackX + 4, gy + trackH, 0xFF000000);
            g.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, 0xFF8B8B8B);
        }

        g.drawString(font, filtered.size() + " / " + all.size(), gx, pt + 138, 0x888888, false);

        super.render(g, mouseX, mouseY, partialTick);

        if (hovered >= 0) {
            Entry e = filtered.get(hovered);
            g.renderComponentTooltip(font, List.of(
                    e.stack().getHoverName(),
                    Component.literal(e.id().toString()).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    // ---------- ввід ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int idx = indexAt(mouseX, mouseY);
            if (idx >= 0) {
                onPick.accept(filtered.get(idx).id());
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(delta), maxScroll()));
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
