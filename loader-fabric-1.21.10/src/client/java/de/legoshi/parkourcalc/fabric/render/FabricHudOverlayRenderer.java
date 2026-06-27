package de.legoshi.parkourcalc.fabric.render;

import de.legoshi.parkourcalc.core.ui.theme.MacroBadgeStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Top-right MACRO badge shown while playback drives the real player. */
public final class FabricHudOverlayRenderer {

    public void render(GuiGraphics context) {
        Minecraft client = Minecraft.getInstance();
        Font tr = client.font;
        String label = MacroBadgeStyle.LABEL;
        int x = context.guiWidth() - tr.width(label) - 4;
        context.drawString(tr, label, x, 4, MacroBadgeStyle.COLOR_ARGB, true);
    }
}
