package de.legoshi.parkourcalc.fabric.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Top-left MACRO badge shown while playback drives the real player. */
public final class FabricHudOverlayRenderer {

    private static final String LABEL = "MACRO";
    private static final int COLOR = 0x4DFFFFFF;

    public void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        int w = tr.getWidth(LABEL) + 6;
        int h = tr.fontHeight + 4;
        int x = 4;
        int y = 4;
        context.fill(x, y, x + w, y + 1, COLOR);
        context.fill(x, y + h - 1, x + w, y + h, COLOR);
        context.fill(x, y, x + 1, y + h, COLOR);
        context.fill(x + w - 1, y, x + w, y + h, COLOR);
        context.drawText(tr, LABEL, x + 3, y + 2, COLOR, false);
    }
}
