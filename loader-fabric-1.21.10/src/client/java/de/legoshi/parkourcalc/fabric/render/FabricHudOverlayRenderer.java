package de.legoshi.parkourcalc.fabric.render;

import de.legoshi.parkourcalc.core.ui.theme.MacroBadgeStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Top-right MACRO badge shown while playback drives the real player. */
public final class FabricHudOverlayRenderer {

    public void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        String label = MacroBadgeStyle.LABEL;
        int x = context.getScaledWindowWidth() - tr.getWidth(label) - 4;
        context.drawText(tr, label, x, 4, MacroBadgeStyle.COLOR_ARGB, true);
    }
}
