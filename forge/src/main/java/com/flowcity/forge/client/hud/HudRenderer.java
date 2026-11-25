package com.flowcity.forge.client.hud;

import com.flowcity.common.hud.HudElement;
import com.flowcity.common.hud.HudElementConfig;
import com.flowcity.common.hud.HudLayout;
import com.flowcity.common.protocol.packets.MoneyStatePacket;
import com.flowcity.common.protocol.packets.PlayerStatePacket;
import com.flowcity.forge.client.ClientConfig;
import com.flowcity.forge.client.ClientState;
import com.flowcity.forge.client.screen.HudEditorScreen;
import com.flowcity.forge.client.screen.Theme;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.StringTextComponent;

import java.util.Locale;

/**
 * Draws the FlowCity HUD. Every value comes from the server (ClientState); nothing is hardcoded.
 * Without data from the server the main HUD is simply not drawn; only the editor preview shows "-".
 */
public final class HudRenderer {

    private static final ResourceLocation LOGO = new ResourceLocation("flowcity", "textures/gui/logo.png");
    private static final ResourceLocation UNIFORM_FONT = new ResourceLocation("minecraft", "uniform");

    private static double shownCash;
    private static double shownBank;
    private static boolean moneyInitialised;
    private static long lastNanos;

    private HudRenderer() {
    }

    /** Called from the overlay event for the normal in-game HUD. */
    public static void onOverlay(MatrixStack matrix) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientConfig.HUD_ENABLED.get()
                || mc.screen instanceof HudEditorScreen || ClientState.self() == null) {
            return;
        }
        render(matrix, ClientState.hudLayout(), false);
    }

    /** Draws every enabled element of {@code layout}. {@code preview} = editor mode (placeholders allowed). */
    public static void render(MatrixStack matrix, HudLayout layout, boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float fade = preview ? 1F : Math.min(1F, ClientState.selfAgeMillis() / 400F);
        animateMoney();
        for (HudElement e : HudElement.values()) {
            HudElementConfig cfg = layout.get(e);
            if (cfg == null || !cfg.enabled) {
                continue;
            }
            int[] r = bounds(e, layout, sw, sh);
            double scale = scaleOf(cfg, layout);
            matrix.pushPose();
            matrix.translate(r[0], r[1], 0);
            matrix.scale((float) scale, (float) scale, 1F);
            drawElement(matrix, mc.font, e, cfg, layout.font, fade, preview);
            matrix.popPose();
        }
        RenderSystem.color4f(1F, 1F, 1F, 1F);
    }

    /** {x, y, width, height} of an element on screen, in GUI pixels. */
    public static int[] bounds(HudElement e, HudLayout layout, int sw, int sh) {
        HudElementConfig c = layout.get(e);
        double s = scaleOf(c, layout);
        int w = (int) Math.round(e.width * s);
        int h = (int) Math.round(e.height * s);
        int x = c.anchoredRight() ? sw - c.dx - w : c.dx;
        int y = c.anchoredBottom() ? sh - c.dy - h : c.dy;
        return new int[]{x, y, w, h};
    }

    private static double scaleOf(HudElementConfig c, HudLayout layout) {
        return (c.scalePercent / 100D) * (layout.uiScalePercent / 100D);
    }

    /** Default accent per element when the player did not pick a colour. */
    public static int defaultAccent(HudElement e) {
        return e == HudElement.ID ? Theme.accent2() : Theme.accent();
    }

    // ------------------------------------------------------------------ drawing

    private static void drawElement(MatrixStack m, FontRenderer font, HudElement e, HudElementConfig cfg,
                                    int fontMode, float fade, boolean preview) {
        int w = e.width;
        int h = e.height;
        int panelAlpha = Math.max(0, Math.min(255, Math.round(cfg.alpha * fade)));
        int textAlpha = Math.max(5, Math.round(255 * fade));
        int accent = cfg.accent >= 0 ? (0xFF000000 | cfg.accent) : defaultAccent(e);
        int white = (textAlpha << 24) | 0xFFFFFF;
        int gray = (textAlpha << 24) | 0xAAAAAA;
        PlayerStatePacket self = ClientState.self();

        if (e == HudElement.RANK && self != null) {
            accent = 0xFF000000 | (self.rankColor & 0xFFFFFF);
        }
        AbstractGui.fill(m, 0, 0, w, h, (panelAlpha << 24) | Theme.panelRgb());
        AbstractGui.fill(m, 0, 0, 2, h, withAlpha(accent, textAlpha));

        switch (e) {
            case LOGO:
                drawLogo(m, font, fontMode, accent, fade, textAlpha);
                break;
            case ID:
                drawLabelAndValue(m, font, fontMode, I18n.get("hud.flowcity.label.id"),
                        self == null ? "-" : self.idLabel, w, gray, withAlpha(accent, textAlpha));
                break;
            case NAME:
                drawFit(m, font, text(self == null ? "-" : self.displayName, fontMode), 6, w - 10, h, white);
                break;
            case RANK: {
                String rank = self == null ? "-" : self.rankName;
                int color = self == null ? white : withAlpha(0xFF000000 | (self.rankColor & 0xFFFFFF), textAlpha);
                drawFit(m, font, text(rank, fontMode), 6, w - 10, h, color);
                break;
            }
            case PLAYTIME:
                drawFit(m, font, text(self == null ? "-" : duration(ClientState.selfPlaytimeSeconds()), fontMode),
                        6, w - 10, h, white);
                break;
            case CASH:
                drawLabelAndValue(m, font, fontMode, I18n.get("hud.flowcity.label.cash"),
                        moneyText(true), w, gray, white);
                break;
            case BANK:
                drawLabelAndValue(m, font, fontMode, I18n.get("hud.flowcity.label.bank"),
                        moneyText(false), w, gray, white);
                break;
            default:
                break;
        }
    }

    private static void drawLogo(MatrixStack m, FontRenderer font, int fontMode, int accent, float fade, int textAlpha) {
        Minecraft mc = Minecraft.getInstance();
        int w = HudElement.LOGO.width;
        AbstractGui.fill(m, 2, 0, w, 1, withAlpha(accent, textAlpha));
        mc.getTextureManager().bind(LOGO);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.color4f(1F, 1F, 1F, fade);
        m.pushPose();
        m.translate(3, 3, 0);
        m.scale(0.25F, 0.25F, 1F);
        AbstractGui.blit(m, 0, 0, 0F, 0F, 256, 256, 256, 256);
        m.popPose();
        RenderSystem.color4f(1F, 1F, 1F, 1F);
        ITextComponent label = text(I18n.get("hud.flowcity.label.roleplay"), fontMode);
        int lw = font.width(label);
        float f = Math.min(1F, (w - 8F) / Math.max(1, lw));
        m.pushPose();
        m.translate((w - lw * f) / 2F, 71, 0);
        m.scale(f, f, 1F);
        font.draw(m, label, 0F, 0F, withAlpha(accent, textAlpha));
        m.popPose();
    }

    private static void drawLabelAndValue(MatrixStack m, FontRenderer font, int fontMode, String label, String value,
                                          int boxWidth, int labelColor, int valueColor) {
        ITextComponent l = text(label, fontMode);
        ITextComponent v = text(value, fontMode);
        float labelScale = 0.75F;
        int lw = (int) Math.ceil(font.width(l) * labelScale);
        m.pushPose();
        m.translate(6, 5.5F, 0);
        m.scale(labelScale, labelScale, 1F);
        font.draw(m, l, 0F, 0F, labelColor);
        m.popPose();
        int room = boxWidth - 6 - lw - 6 - 4;
        int vw = font.width(v);
        float f = Math.min(1F, room / (float) Math.max(1, vw));
        m.pushPose();
        m.translate(boxWidth - 5 - vw * f, (16 - 8 * f) / 2F, 0);
        m.scale(f, f, 1F);
        font.draw(m, v, 0F, 0F, valueColor);
        m.popPose();
    }

    private static void drawFit(MatrixStack m, FontRenderer font, ITextComponent t, int x, int maxWidth, int boxHeight,
                                int color) {
        int tw = font.width(t);
        float f = Math.min(1F, maxWidth / (float) Math.max(1, tw));
        m.pushPose();
        m.translate(x, (boxHeight - 8 * f) / 2F, 0);
        m.scale(f, f, 1F);
        font.draw(m, t, 0F, 0F, color);
        m.popPose();
    }

    private static ITextComponent text(String s, int fontMode) {
        StringTextComponent t = new StringTextComponent(s);
        if (fontMode == HudLayout.FONT_UNIFORM) {
            t.withStyle(Style.EMPTY.withFont(UNIFORM_FONT));
        }
        return t;
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ values

    /** Moves the displayed balance smoothly towards the server value (count-up animation). */
    private static void animateMoney() {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (now - lastNanos) / 1.0E9);
        lastNanos = now;
        MoneyStatePacket money = ClientState.money();
        if (money == null) {
            moneyInitialised = false;
            return;
        }
        if (!moneyInitialised) {
            shownCash = money.cashCents;
            shownBank = money.bankCents;
            moneyInitialised = true;
            return;
        }
        double k = Math.min(1D, dt * 8D);
        shownCash = approach(shownCash, money.cashCents, k);
        shownBank = approach(shownBank, money.bankCents, k);
    }

    private static double approach(double shown, double target, double k) {
        double diff = target - shown;
        return Math.abs(diff) < 1D ? target : shown + diff * k;
    }

    private static String moneyText(boolean cash) {
        MoneyStatePacket money = ClientState.money();
        if (money == null) {
            return "-";
        }
        return format(Math.round(cash ? shownCash : shownBank), money.symbol, money.decimals);
    }

    /** 1234567 cents, "$", 0 -> "$12,345". Pure function, no locale surprises. */
    static String format(long cents, String symbol, int decimals) {
        boolean negative = cents < 0;
        long abs = Math.abs(cents);
        long whole = abs / 100;
        String grouped = String.format(Locale.US, "%,d", whole);
        String out = decimals >= 1 ? grouped + "." + String.format(Locale.US, "%02d", abs % 100) : grouped;
        return (negative ? "-" : "") + symbol + out;
    }

    static String duration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }
}
