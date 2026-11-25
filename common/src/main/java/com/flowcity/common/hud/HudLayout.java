package com.flowcity.common.hud;

import java.util.EnumMap;
import java.util.Map;

/**
 * A complete HUD layout (one preset). Stored in the database and sent over the wire as one
 * compact string, so server and client share exactly one codec.
 *
 * <pre>
 *   v1|font|uiScalePercent|ELEMENT,enabled,anchor,dx,dy,scale,alpha,accent;ELEMENT,...
 * </pre>
 * The server parses every layout a client sends and stores its canonical re-encoding, so a
 * hostile client can never store anything that is not a valid, range-clamped layout.
 */
public final class HudLayout {

    public static final String FORMAT = "v1";
    public static final int MAX_ENCODED_LENGTH = 1024;

    public static final int FONT_DEFAULT = 0;
    public static final int FONT_UNIFORM = 1;
    public static final int FONT_COUNT = 2;

    public static final int MIN_UI_SCALE = 50;
    public static final int MAX_UI_SCALE = 200;

    public int font;
    public int uiScalePercent;
    private final Map<HudElement, HudElementConfig> elements = new EnumMap<>(HudElement.class);

    public HudLayout(int font, int uiScalePercent) {
        this.font = Math.max(0, Math.min(FONT_COUNT - 1, font));
        this.uiScalePercent = Math.max(MIN_UI_SCALE, Math.min(MAX_UI_SCALE, uiScalePercent));
    }

    public HudElementConfig get(HudElement e) {
        return elements.get(e);
    }

    public void put(HudElement e, HudElementConfig c) {
        elements.put(e, c);
    }

    public HudLayout copy() {
        HudLayout l = new HudLayout(font, uiScalePercent);
        for (Map.Entry<HudElement, HudElementConfig> en : elements.entrySet()) {
            l.elements.put(en.getKey(), en.getValue().copy());
        }
        return l;
    }

    /**
     * The FlowCity default: logo top right, ID / name / rank / playtime in one row below it,
     * cash and bank in a second row. Nothing in the top-left corner (no clock, no date).
     */
    public static HudLayout defaults() {
        HudLayout l = new HudLayout(FONT_DEFAULT, 100);
        int tr = HudElementConfig.ANCHOR_TOP_RIGHT;
        int gap = 3;
        int edge = 6;
        l.put(HudElement.LOGO, new HudElementConfig(true, tr, edge, edge, 100, 190, -1));
        int rowY = edge + HudElement.LOGO.height + gap;
        int x = edge;
        l.put(HudElement.PLAYTIME, new HudElementConfig(true, tr, x, rowY, 100, 190, -1));
        x += HudElement.PLAYTIME.width + gap;
        l.put(HudElement.RANK, new HudElementConfig(true, tr, x, rowY, 100, 190, -1));
        x += HudElement.RANK.width + gap;
        l.put(HudElement.NAME, new HudElementConfig(true, tr, x, rowY, 100, 190, -1));
        x += HudElement.NAME.width + gap;
        l.put(HudElement.ID, new HudElementConfig(true, tr, x, rowY, 100, 190, -1));
        int row2 = rowY + HudElement.ID.height + gap;
        l.put(HudElement.BANK, new HudElementConfig(true, tr, edge, row2, 100, 190, -1));
        l.put(HudElement.CASH, new HudElementConfig(true, tr,
                edge + HudElement.BANK.width + gap, row2, 100, 190, -1));
        return l;
    }

    public String encode() {
        StringBuilder sb = new StringBuilder(256);
        sb.append(FORMAT).append('|').append(font).append('|').append(uiScalePercent).append('|');
        boolean first = true;
        for (HudElement e : HudElement.values()) {
            HudElementConfig c = elements.get(e);
            if (c == null) {
                continue;
            }
            if (!first) {
                sb.append(';');
            }
            first = false;
            sb.append(e.key).append(',').append(c.enabled ? 1 : 0).append(',').append(c.anchor).append(',')
                    .append(c.dx).append(',').append(c.dy).append(',').append(c.scalePercent).append(',')
                    .append(c.alpha).append(',').append(c.accent);
        }
        return sb.toString();
    }

    /**
     * Strict syntax, lenient ranges: malformed text, unknown elements or duplicates are rejected
     * ({@link IllegalArgumentException}); numbers outside their range are clamped. Elements missing
     * from the text get their default.
     */
    public static HudLayout parse(String text) {
        if (text == null || text.length() > MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("layout missing or too long");
        }
        String[] head = text.split("\\|", -1);
        if (head.length != 4 || !FORMAT.equals(head[0])) {
            throw new IllegalArgumentException("bad layout header");
        }
        HudLayout defaults = defaults();
        HudLayout l = new HudLayout(number(head[1]), number(head[2]));
        Map<HudElement, HudElementConfig> seen = new EnumMap<>(HudElement.class);
        if (!head[3].isEmpty()) {
            for (String part : head[3].split(";", -1)) {
                String[] f = part.split(",", -1);
                if (f.length != 8) {
                    throw new IllegalArgumentException("bad element entry");
                }
                HudElement e = HudElement.fromKey(f[0]);
                if (e == null || seen.containsKey(e)) {
                    throw new IllegalArgumentException("unknown or duplicate element");
                }
                seen.put(e, new HudElementConfig(number(f[1]) != 0, number(f[2]), number(f[3]), number(f[4]),
                        number(f[5]), number(f[6]), number(f[7])));
            }
        }
        for (HudElement e : HudElement.values()) {
            HudElementConfig c = seen.get(e);
            l.put(e, c != null ? c : defaults.get(e).copy());
        }
        return l;
    }

    private static int number(String s) {
        if (s.isEmpty() || s.length() > 9) {
            throw new IllegalArgumentException("bad number");
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad number");
        }
    }
}
