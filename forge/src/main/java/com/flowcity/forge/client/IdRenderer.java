package com.flowcity.forge.client;

import com.flowcity.common.protocol.packets.NearbyIdsPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraftforge.client.event.RenderNameplateEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * Draws the server-provided RP ID above players while the "show IDs" key is held (LEFT ALT by
 * default) or permanently after /viewid. Vanilla name tags of players are suppressed, so a
 * Minecraft username is never drawn above a player.
 */
public final class IdRenderer {

    private static boolean target;
    private static float fromAlpha;
    private static long changedAt;

    private IdRenderer() {
    }

    /** Called every client tick with whether IDs should be visible. */
    static void setVisible(boolean visible) {
        if (visible != target) {
            fromAlpha = alpha();
            changedAt = System.currentTimeMillis();
            target = visible;
        }
    }

    /** 0..1, animated between key press/release. */
    static float alpha() {
        int fade = ClientConfig.ID_FADE_MILLIS.get();
        float to = target ? 1f : 0f;
        if (fade <= 0) {
            return to;
        }
        float progress = Math.min(1f, (System.currentTimeMillis() - changedAt) / (float) fade);
        return fromAlpha + (to - fromAlpha) * progress;
    }

    static void onNameplate(RenderNameplateEvent event) {
        if (event.getEntity() instanceof PlayerEntity && ClientConfig.HIDE_VANILLA_NAMETAGS.get()) {
            event.setResult(Event.Result.DENY);
        }
    }

    static void onRenderPlayer(RenderPlayerEvent.Post event) {
        float alpha = alpha();
        if (alpha < 0.05f) {
            return;
        }
        PlayerEntity player = event.getPlayer();
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player || !(player instanceof AbstractClientPlayerEntity) || player.isInvisible()) {
            return;
        }
        NearbyIdsPacket.Entry entry = ClientState.idFor(player.getId());
        if (entry == null || mc.player == null) {
            return;
        }
        double max = ClientConfig.ID_MAX_DISTANCE.get();
        if (player.distanceToSqr(mc.player) > max * max) {
            return;
        }

        MatrixStack matrix = event.getMatrixStack();
        FontRenderer font = mc.font;
        String text = entry.label;
        float scale = 0.025f * ClientConfig.ID_SCALE.get().floatValue();

        matrix.pushPose();
        matrix.translate(0.0D, player.getBbHeight() + 0.5D, 0.0D);
        matrix.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        matrix.scale(-scale, -scale, scale);
        Matrix4f pose = matrix.last().pose();

        int textAlpha = Math.max(5, Math.round(alpha * 255f));
        int color = (entry.color & 0x00FFFFFF) | (textAlpha << 24);
        int background = Math.round(alpha * 0.45f * 255f) << 24;
        float x = -font.width(text) / 2f;
        // see-through pass (visible behind blocks, faint) then the normal pass
        font.drawInBatch(text, x, 0, (color & 0x00FFFFFF) | (Math.max(5, textAlpha / 3) << 24), false, pose,
                event.getBuffers(), true, background, event.getLight());
        font.drawInBatch(text, x, 0, color, false, pose, event.getBuffers(), false, 0, event.getLight());
        matrix.popPose();
    }
}
