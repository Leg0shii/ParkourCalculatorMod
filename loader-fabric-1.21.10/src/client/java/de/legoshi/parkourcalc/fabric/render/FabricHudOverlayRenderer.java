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
        int color = MacroBadgeStyle.COLOR_ARGB;
        int w = tr.getWidth(label) + 6;
        int h = tr.fontHeight + 4;
        int x = context.getScaledWindowWidth() - w - 4;
        int y = 4;
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
        context.drawText(tr, label, x + 3, y + 2, color, false);
    }
}
