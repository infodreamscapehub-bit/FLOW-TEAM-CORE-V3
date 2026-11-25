package com.flowcity.core.rank;

import java.util.Objects;

/** How a rank is shown: name, ARGB colour, icon id. Purely display - LuckPerms owns the rank. */
public final class RankDisplay {

    public final String group;
    public final String display;
    public final int color;
    public final String icon;
    public final int priority;

    public RankDisplay(String group, String display, int color, String icon, int priority) {
        this.group = group;
        this.display = display;
        this.color = color;
        this.icon = icon;
        this.priority = priority;
    }

    /** Legacy chat colour closest to the ARGB colour, for chat/GUI text. */
    public String chatColored() {
        return nearestChatColor(color) + display;
    }

    static String nearestChatColor(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int[][] palette = {
                {0, 0, 0}, {0, 0, 170}, {0, 170, 0}, {0, 170, 170}, {170, 0, 0}, {170, 0, 170}, {255, 170, 0},
                {170, 170, 170}, {85, 85, 85}, {85, 85, 255}, {85, 255, 85}, {85, 255, 255}, {255, 85, 85},
                {255, 85, 255}, {255, 255, 85}, {255, 255, 255}};
        String codes = "0123456789abcdef";
        int best = 15;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            long dr = r - palette[i][0], dg = g - palette[i][1], db = b - palette[i][2];
            long d = dr * dr + dg * dg + db * db;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return "§" + codes.charAt(best);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RankDisplay)) {
            return false;
        }
        RankDisplay r = (RankDisplay) o;
        return color == r.color && priority == r.priority && group.equals(r.group)
                && display.equals(r.display) && icon.equals(r.icon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, display, color, icon, priority);
    }
}
