package com.flowcity.forge.client.screen;

import com.flowcity.common.protocol.packets.ProfileRequestPacket;
import com.flowcity.forge.client.ClientNetwork;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import org.lwjgl.glfw.GLFW;

/** F6: type an RP ID, the server answers with the profile (or "not found"). */
public final class IdSearchScreen extends Screen {

    private static final int W = 220;
    private static final int H = 96;

    private TextFieldWidget field;
    private Button searchButton;
    private String error = "";

    public IdSearchScreen() {
        super(new TranslationTextComponent("screen.flowcity.id_search"));
    }

    @Override
    protected void init() {
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        field = new TextFieldWidget(font, x + 12, y + 34, W - 24, 18, new StringTextComponent("ID"));
        field.setMaxLength(9);
        field.setFilter(s -> s.isEmpty() || s.matches("[0-9]{1,9}"));
        children.add(field);
        setInitialFocus(field);

        searchButton = addButton(new Button(x + 12, y + 62, (W - 30) / 2, 20,
                new TranslationTextComponent("screen.flowcity.search"), b -> submit()));
        addButton(new Button(x + 18 + (W - 30) / 2, y + 62, (W - 30) / 2, 20,
                new TranslationTextComponent("screen.flowcity.close"), b -> onClose()));
    }

    private void submit() {
        String raw = field.getValue().trim();
        int id;
        try {
            id = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            id = 0;
        }
        if (id <= 0) {
            error = I18n.get("screen.flowcity.invalid_id");
            return;
        }
        error = "";
        searchButton.active = false;
        ClientNetwork.send(new ProfileRequestPacket(id).encode());
        // The server answers with a ProfilePacket, which replaces this screen.
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        field.tick();
    }

    @Override
    public void render(MatrixStack matrix, int mouseX, int mouseY, float partialTicks) {
        renderBackground(matrix);
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        fill(matrix, x, y, x + W, y + H, Theme.PANEL);
        fill(matrix, x, y, x + W, y + 2, Theme.ORANGE);
        font.draw(matrix, "FLOWCITY", x + 12, y + 9, Theme.ORANGE);
        font.draw(matrix, I18n.get("screen.flowcity.id_search"), x + 12 + font.width("FLOWCITY "), y + 9, Theme.WHITE);
        font.draw(matrix, I18n.get("screen.flowcity.enter_id"), x + 12, y + 22, Theme.GRAY);
        field.render(matrix, mouseX, mouseY, partialTicks);
        if (!error.isEmpty()) {
            font.draw(matrix, error, x + 12, y + H + 4, Theme.RED);
        }
        super.render(matrix, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
