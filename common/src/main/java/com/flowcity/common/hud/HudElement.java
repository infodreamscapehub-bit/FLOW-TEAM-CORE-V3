package com.flowcity.common.hud;

/**
 * The movable parts of the main HUD. {@code key} is the stable id used in saved layouts - never rename it.
 * Width/height are the unscaled box size in GUI pixels.
 */
public enum HudElement {
    LOGO("logo", 70, 70),
    ID("id", 46, 16),
    NAME("name", 96, 16),
    RANK("rank", 70, 16),
    PLAYTIME("playtime", 44, 16),
    CASH("cash", 96, 16),
    BANK("bank", 96, 16);

    public final String key;
    public final int width;
    public final int height;

    HudElement(String key, int width, int height) {
        this.key = key;
        this.width = width;
        this.height = height;
    }

    public static HudElement fromKey(String key) {
        for (HudElement e : values()) {
            if (e.key.equals(key)) {
                return e;
            }
        }
        return null;
    }
}
