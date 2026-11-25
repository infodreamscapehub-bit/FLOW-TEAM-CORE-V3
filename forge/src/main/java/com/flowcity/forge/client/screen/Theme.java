package com.flowcity.forge.client.screen;

import com.flowcity.forge.client.ClientConfig;

/** Central FlowCity UI colours (ARGB). The HUD reads the configurable values (accent(), accent2(), panelRgb()); older screens still use the constants. */
public final class Theme {

    public static final int PANEL = 0xD0121212;
    public static final int PANEL_LIGHT = 0xD01E1E1E;
    public static final int ORANGE = 0xFFFF8C00;
    public static final int YELLOW = 0xFFFFC400;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFFAAAAAA;
    public static final int DARK_GRAY = 0xFF555555;
    public static final int GREEN = 0xFF55FF55;
    public static final int RED = 0xFFFF5555;

    /** Accent choices in the HUD editor; -1 = "theme default" (follows flowcity-client.toml). */
    public static final int[] PALETTE = {-1, 0xFF8C00, 0xFFC400, 0xFFFFFF, 0x3FA9F5, 0x55FF55, 0xFF5555, 0xAAAAAA};

    private Theme() {
    }

    /** Configurable main accent, opaque ARGB. */
    public static int accent() {
        return 0xFF000000 | hex(ClientConfig.THEME_ACCENT.get(), 0xFF8C00);
    }

    /** Configurable second accent, opaque ARGB. */
    public static int accent2() {
        return 0xFF000000 | hex(ClientConfig.THEME_ACCENT_2.get(), 0xFFC400);
    }

    /** Configurable panel colour, RGB without alpha. */
    public static int panelRgb() {
        return hex(ClientConfig.THEME_PANEL.get(), 0x121212);
    }

    private static int hex(String text, int fallback) {
        try {
            String t = text.trim();
            if (t.startsWith("#")) {
                t = t.substring(1);
            }
            return Integer.parseInt(t, 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
