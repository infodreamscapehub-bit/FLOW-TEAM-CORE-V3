package com.flowcity.forge.client.screen;

import com.flowcity.common.protocol.packets.ProfilePacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TranslationTextComponent;

import java.text.SimpleDateFormat;
import java.util.Date;

/** Player profile (F7 = own, F6 / /viewid = by RP ID). Shows only what the server sent. */
public final class ProfileScreen extends Screen {

    private static final int W = 240;

    private final ProfilePacket profile;
    private int panelHeight;

    public ProfileScreen(ProfilePacket profile) {
        super(new TranslationTextComponent("screen.flowcity.profile"));
        this.profile = profile;
    }

    @Override
    protected void init() {
        panelHeight = !profile.found ? 80 : (profile.username.isEmpty() ? 150 : 176);
        int x = (width - W) / 2;
        int y = (height - panelHeight) / 2;
        addButton(new Button(x + (W - 90) / 2, y + panelHeight - 28, 90, 20,
                new TranslationTextComponent("screen.flowcity.close"), b -> onClose()));
    }

    @Override
    public void render(MatrixStack matrix, int mouseX, int mouseY, float partialTicks) {
        renderBackground(matrix);
        int x = (width - W) / 2;
        int y = (height - panelHeight) / 2;
        fill(matrix, x, y, x + W, y + panelHeight, Theme.PANEL);
        fill(matrix, x, y, x + W, y + 2, Theme.ORANGE);
        font.draw(matrix, "FLOWCITY", x + 12, y + 9, Theme.ORANGE);
        font.draw(matrix, I18n.get("screen.flowcity.profile"), x + 12 + font.width("FLOWCITY "), y + 9, Theme.WHITE);

        if (!profile.found) {
            font.draw(matrix, profile.error, x + 12, y + 32, Theme.RED);
            super.render(matrix, mouseX, mouseY, partialTicks);
            return;
        }

        // big ID badge
        matrix.pushPose();
        matrix.translate(x + 12, y + 26, 0);
        matrix.scale(2f, 2f, 1f);
        font.draw(matrix, "ID " + profile.idLabel, 0, 0, Theme.YELLOW);
        matrix.popPose();

        // rank box
        String rank = profile.rankName;
        int rw = font.width(rank) + 12;
        int rx = x + W - 12 - rw;
        fill(matrix, rx, y + 26, rx + rw, y + 42, Theme.PANEL_LIGHT);
        fill(matrix, rx, y + 26, rx + 2, y + 42, profile.rankColor | 0xFF000000);
        font.draw(matrix, rank, rx + 7, y + 30, profile.rankColor | 0xFF000000);

        int line = y + 54;
        line = row(matrix, x, line, "screen.flowcity.name", profile.displayName, Theme.WHITE);
        line = row(matrix, x, line, "screen.flowcity.playtime", duration(profile.playtimeSeconds), Theme.WHITE);
        line = row(matrix, x, line, "screen.flowcity.status",
                I18n.get(profile.online ? "screen.flowcity.online" : "screen.flowcity.offline"),
                profile.online ? Theme.GREEN : Theme.RED);
        line = row(matrix, x, line, "screen.flowcity.first_join", date(profile.firstJoinMillis), Theme.WHITE);
        if (!profile.online) {
            line = row(matrix, x, line, "screen.flowcity.last_seen", date(profile.lastSeenMillis), Theme.WHITE);
        }
        if (!profile.username.isEmpty()) {
            fill(matrix, x + 12, line + 1, x + W - 12, line + 2, Theme.DARK_GRAY);
            line += 6;
            line = row(matrix, x, line, "screen.flowcity.account", profile.username, Theme.GRAY);
            font.draw(matrix, profile.uuid, x + 12, line, Theme.DARK_GRAY);
        }
        super.render(matrix, mouseX, mouseY, partialTicks);
    }

    private int row(MatrixStack matrix, int x, int y, String key, String value, int color) {
        font.draw(matrix, I18n.get(key), x + 12, y, Theme.GRAY);
        font.draw(matrix, value, x + 100, y, color);
        return y + 13;
    }

    private static String duration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    private static String date(long millis) {
        return millis <= 0 ? "-" : new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(millis));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
