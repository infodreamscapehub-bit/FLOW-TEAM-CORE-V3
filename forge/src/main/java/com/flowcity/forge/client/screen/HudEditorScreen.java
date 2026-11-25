package com.flowcity.forge.client.screen;

import com.flowcity.common.hud.HudElement;
import com.flowcity.common.hud.HudElementConfig;
import com.flowcity.common.hud.HudLayout;
import com.flowcity.common.protocol.packets.HudConfigPacket;
import com.flowcity.common.protocol.packets.HudSavePacket;
import com.flowcity.forge.client.ClientNetwork;
import com.flowcity.forge.client.ClientState;
import com.flowcity.forge.client.hud.HudRenderer;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/**
 * Live HUD editor (default key H). The working layout is drawn by the real HUD renderer, so what
 * you see is exactly what the HUD will look like. Nothing is stored until "Save": the server then
 * validates the layout and keeps it per player (3 preset slots).
 */
public final class HudEditorScreen extends Screen {

    private static final int PANEL_H = 52;

    private HudLayout working;
    private HudElement selected;
    private HudElement dragging;
    private double grabX;
    private double grabY;
    private int slot;
    private int versionAtSave = -1;

    private Button toggleButton;
    private Button fontButton;
    private final Button[] slotButtons = new Button[HudConfigPacket.MAX_SLOTS];

    public HudEditorScreen() {
        super(new TranslationTextComponent("screen.flowcity.hud.title"));
        slot = ClientState.hudActiveSlot() == 0 ? 1 : ClientState.hudActiveSlot();
        working = ClientState.hudLayout().copy();
    }

    @Override
    protected void init() {
        int y1 = height - PANEL_H + 6;
        int y2 = height - PANEL_H + 28;

        // row 1: the selected element
        int n1 = 6;
        int bw1 = Math.max(40, Math.min(66, (width - 10) / n1 - 2));
        int x = (width - n1 * (bw1 + 2) + 2) / 2;
        toggleButton = addButton(new Button(x, y1, bw1, 20, label("screen.flowcity.hud.hide"), b -> toggleSelected()));
        x += bw1 + 2;
        addButton(new Button(x, y1, bw1, 20, label("screen.flowcity.hud.color"), b -> cycleColor()));
        x += bw1 + 2;
        addButton(new Button(x, y1, bw1, 20, label("screen.flowcity.hud.alpha_down"), b -> changeAlpha(-16)));
        x += bw1 + 2;
        addButton(new Button(x, y1, bw1, 20, label("screen.flowcity.hud.alpha_up"), b -> changeAlpha(16)));
        x += bw1 + 2;
        addButton(new Button(x, y1, bw1, 20, label("screen.flowcity.hud.size_down"), b -> changeSize(-10)));
        x += bw1 + 2;
        addButton(new Button(x, y1, bw1, 20, label("screen.flowcity.hud.size_up"), b -> changeSize(10)));

        // row 2: whole HUD, presets, actions
        int n2 = 9;
        int bw2 = Math.max(36, Math.min(60, (width - 10) / n2 - 2));
        x = (width - n2 * (bw2 + 2) + 2) / 2;
        fontButton = addButton(new Button(x, y2, bw2, 20, label("screen.flowcity.hud.font.default"), b -> cycleFont()));
        x += bw2 + 2;
        addButton(new Button(x, y2, bw2, 20, label("screen.flowcity.hud.ui_down"), b -> changeUi(-10)));
        x += bw2 + 2;
        addButton(new Button(x, y2, bw2, 20, label("screen.flowcity.hud.ui_up"), b -> changeUi(10)));
        x += bw2 + 2;
        for (int i = 0; i < slotButtons.length; i++) {
            final int s = i + 1;
            slotButtons[i] = addButton(new Button(x, y2, bw2, 20, label("screen.flowcity.hud.slot", s),
                    b -> selectSlot(s)));
            x += bw2 + 2;
        }
        addButton(new Button(x, y2, bw2, 20, label("screen.flowcity.hud.reset"), b -> reset()));
        x += bw2 + 2;
        addButton(new Button(x, y2, bw2, 20, label("screen.flowcity.hud.save"), b -> save()));
        refreshLabels();
    }

    private static ITextComponent label(String key, Object... args) {
        return new TranslationTextComponent(key, args);
    }

    // ------------------------------------------------------------------ actions

    private HudElementConfig cfg() {
        return selected == null ? null : working.get(selected);
    }

    private void toggleSelected() {
        HudElementConfig c = cfg();
        if (c != null) {
            c.enabled = !c.enabled;
            refreshLabels();
        }
    }

    private void cycleColor() {
        HudElementConfig c = cfg();
        if (c == null) {
            return;
        }
        int index = 0;
        for (int i = 0; i < Theme.PALETTE.length; i++) {
            if (Theme.PALETTE[i] == c.accent) {
                index = i;
                break;
            }
        }
        c.accent = Theme.PALETTE[(index + 1) % Theme.PALETTE.length];
        c.clamp();
    }

    private void changeAlpha(int delta) {
        HudElementConfig c = cfg();
        if (c != null) {
            c.alpha += delta;
            c.clamp();
        }
    }

    private void changeSize(int delta) {
        HudElementConfig c = cfg();
        if (c != null) {
            c.scalePercent += delta;
            c.clamp();
            keepOnScreen(selected);
        }
    }

    private void changeUi(int delta) {
        working.uiScalePercent = Math.max(HudLayout.MIN_UI_SCALE,
                Math.min(HudLayout.MAX_UI_SCALE, working.uiScalePercent + delta));
    }

    private void cycleFont() {
        working.font = (working.font + 1) % HudLayout.FONT_COUNT;
        refreshLabels();
    }

    private void selectSlot(int s) {
        slot = s;
        HudConfigPacket.Preset preset = ClientState.hudPreset(s);
        if (preset != null) {
            try {
                working = HudLayout.parse(preset.layout);
            } catch (IllegalArgumentException ignored) {
                // keep the current working layout
            }
        }
        refreshLabels();
    }

    private void reset() {
        working = HudLayout.defaults();
        selected = null;
        refreshLabels();
    }

    private void save() {
        HudConfigPacket.Preset existing = ClientState.hudPreset(slot);
        String name = existing != null ? existing.name : "Preset " + slot;
        versionAtSave = ClientState.hudVersion();
        ClientNetwork.send(new HudSavePacket(slot, name, working.encode()).encode());
    }

    private void refreshLabels() {
        HudElementConfig c = cfg();
        if (toggleButton != null) {
            toggleButton.setMessage(label(c != null && !c.enabled
                    ? "screen.flowcity.hud.show" : "screen.flowcity.hud.hide"));
        }
        if (fontButton != null) {
            fontButton.setMessage(label("screen.flowcity.hud.font", I18n.get(working.font == HudLayout.FONT_UNIFORM
                    ? "screen.flowcity.hud.font.uniform" : "screen.flowcity.hud.font.default")));
        }
        for (int i = 0; i < slotButtons.length; i++) {
            if (slotButtons[i] != null) {
                slotButtons[i].setMessage(label(slot == i + 1
                        ? "screen.flowcity.hud.slot_active" : "screen.flowcity.hud.slot", i + 1));
            }
        }
    }

    // ------------------------------------------------------------------ mouse

    private int[] boundsOf(HudElement e) {
        return HudRenderer.bounds(e, working, width, height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0 || mouseY >= height - PANEL_H) {
            return false;
        }
        HudElement[] all = HudElement.values();
        for (int i = all.length - 1; i >= 0; i--) {
            int[] r = boundsOf(all[i]);
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                selected = all[i];
                dragging = all[i];
                grabX = mouseX - r[0];
                grabY = mouseY - r[1];
                refreshLabels();
                return true;
            }
        }
        selected = null;
        refreshLabels();
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null && button == 0) {
            moveTo(dragging, (int) Math.round(mouseX - grabX), (int) Math.round(mouseY - grabY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (selected != null && delta != 0) {
            changeSize(delta > 0 ? 5 : -5);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** Puts the element's top-left corner at (x, y) and re-anchors it to the nearest screen corner. */
    private void moveTo(HudElement e, int x, int y) {
        int[] r = boundsOf(e);
        int w = r[2];
        int h = r[3];
        int nx = Math.max(0, Math.min(width - w, x));
        int ny = Math.max(0, Math.min(height - PANEL_H - h, y));
        boolean right = nx + w / 2 > width / 2;
        boolean bottom = ny + h / 2 > (height - PANEL_H) / 2;
        HudElementConfig c = working.get(e);
        c.anchor = bottom ? (right ? HudElementConfig.ANCHOR_BOTTOM_RIGHT : HudElementConfig.ANCHOR_BOTTOM_LEFT)
                : (right ? HudElementConfig.ANCHOR_TOP_RIGHT : HudElementConfig.ANCHOR_TOP_LEFT);
        c.dx = right ? width - (nx + w) : nx;
        c.dy = bottom ? height - (ny + h) : ny;
        c.clamp();
    }

    /** After a resize the element could leave the screen: re-place it where it is now, clamped. */
    private void keepOnScreen(HudElement e) {
        int[] r = boundsOf(e);
        moveTo(e, r[0], r[1]);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(MatrixStack matrix, int mouseX, int mouseY, float partialTicks) {
        fill(matrix, 0, 0, width, height, 0x66000000);
        HudRenderer.render(matrix, working, true);

        for (HudElement e : HudElement.values()) {
            int[] r = boundsOf(e);
            boolean on = working.get(e).enabled;
            int color = e == selected ? Theme.accent() : (on ? 0x88FFFFFF : 0x66FFFFFF);
            if (!on) {
                fill(matrix, r[0], r[1], r[0] + r[2], r[1] + r[3], 0x33FFFFFF);
            }
            outline(matrix, r[0], r[1], r[0] + r[2], r[1] + r[3], color);
        }

        fill(matrix, 0, height - PANEL_H, width, height, 0xE0101010);
        fill(matrix, 0, height - PANEL_H, width, height - PANEL_H + 1, Theme.accent());
        font.draw(matrix, I18n.get("screen.flowcity.hud.title"), 8, 8, Theme.accent());
        font.draw(matrix, I18n.get("screen.flowcity.hud.help"), 8 + font.width(I18n.get("screen.flowcity.hud.title")) + 10,
                8, Theme.GRAY);
        String sel = selected == null ? I18n.get("screen.flowcity.hud.none")
                : I18n.get("screen.flowcity.hud.selected", I18n.get("hud.flowcity.element." + selected.key));
        font.draw(matrix, sel, 8, 20, Theme.WHITE);
        String status = versionAtSave < 0 ? "" : (ClientState.hudVersion() != versionAtSave
                ? I18n.get("screen.flowcity.hud.saved") : I18n.get("screen.flowcity.hud.saving"));
        if (!status.isEmpty()) {
            font.draw(matrix, status, width - 8 - font.width(status), 8,
                    ClientState.hudVersion() != versionAtSave ? Theme.GREEN : Theme.GRAY);
        }
        super.render(matrix, mouseX, mouseY, partialTicks);
    }

    private void outline(MatrixStack m, int x1, int y1, int x2, int y2, int color) {
        fill(m, x1 - 1, y1 - 1, x2 + 1, y1, color);
        fill(m, x1 - 1, y2, x2 + 1, y2 + 1, color);
        fill(m, x1 - 1, y1, x1, y2, color);
        fill(m, x2, y1, x2 + 1, y2, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
