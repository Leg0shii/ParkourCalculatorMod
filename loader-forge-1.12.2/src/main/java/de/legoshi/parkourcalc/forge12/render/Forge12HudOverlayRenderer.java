package de.legoshi.parkourcalc.forge12.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

/** Top-right MACRO badge shown while playback drives the real player. */
public final class Forge12HudOverlayRenderer {

    private static final String LABEL = "MACRO";
    private static final int COLOR = 0x55FFFFFF;

    public void render() {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;
        if (fr == null) return;
        ScaledResolution sr = new ScaledResolution(mc);
        int x = sr.getScaledWidth() - fr.getStringWidth(LABEL) - 4;
        fr.drawStringWithShadow(LABEL, x, 4, COLOR);
    }
}
