package com.flowcity.core.hud;

/** Preset name sanitising; Bukkit-free so it can be unit-tested. */
final class HudNames {

    private HudNames() {
    }

    /** Letters/digits/space/-/_ only, at most 16 characters; empty becomes "Preset N". */
    static String clean(String raw, int slot) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length() && sb.length() < 16; i++) {
            char ch = raw.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
                    || ch == ' ' || ch == '-' || ch == '_') {
                sb.append(ch);
            }
        }
        String name = sb.toString().trim();
        return name.isEmpty() ? "Preset " + slot : name;
    }
}
