package com.flowcity.common.hud;

/** Placement and look of one HUD element. Immutable-by-convention copy semantics via {@link #copy()}. */
public final class HudElementConfig {

    public static final int ANCHOR_TOP_LEFT = 0;
    public static final int ANCHOR_TOP_RIGHT = 1;
    public static final int ANCHOR_BOTTOM_LEFT = 2;
    public static final int ANCHOR_BOTTOM_RIGHT = 3;

    public static final int MIN_SCALE = 50;
    public static final int MAX_SCALE = 300;
    public static final int MAX_OFFSET = 4000;

    public boolean enabled;
    /** Screen corner the element is measured from. */
    public int anchor;
    /** Distance in GUI pixels from the anchor corner (always >= 0 in normal use). */
    public int dx;
    public int dy;
    /** Size in percent (100 = natural size). */
    public int scalePercent;
    /** Panel background opacity 0..255. */
    public int alpha;
    /** 0xRRGGBB accent colour, or -1 for the theme default of that element. */
    public int accent;

    public HudElementConfig(boolean enabled, int anchor, int dx, int dy, int scalePercent, int alpha, int accent) {
        this.enabled = enabled;
        this.anchor = anchor;
        this.dx = dx;
        this.dy = dy;
        this.scalePercent = scalePercent;
        this.alpha = alpha;
        this.accent = accent;
        clamp();
    }

    public HudElementConfig copy() {
        return new HudElementConfig(enabled, anchor, dx, dy, scalePercent, alpha, accent);
    }

    /** Forces every field into its legal range (used on anything that came over the network). */
    public void clamp() {
        anchor = Math.max(0, Math.min(3, anchor));
        dx = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, dx));
        dy = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, dy));
        scalePercent = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scalePercent));
        alpha = Math.max(0, Math.min(255, alpha));
        accent = accent < 0 ? -1 : (accent & 0xFFFFFF);
    }

    public boolean anchoredRight() {
        return anchor == ANCHOR_TOP_RIGHT || anchor == ANCHOR_BOTTOM_RIGHT;
    }

    public boolean anchoredBottom() {
        return anchor == ANCHOR_BOTTOM_LEFT || anchor == ANCHOR_BOTTOM_RIGHT;
    }
}
