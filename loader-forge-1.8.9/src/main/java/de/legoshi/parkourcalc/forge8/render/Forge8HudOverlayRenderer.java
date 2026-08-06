package de.legoshi.parkourcalc.forge8.render;

import de.legoshi.parkourcalc.core.ui.theme.MacroBadgeStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

/** Top-right MACRO badge shown while playback drives the real player. */
@SuppressWarnings("DuplicatedCode")
public final class Forge8HudOverlayRenderer {

    public void render() {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRendererObj;
        if (fr == null) return;
        ScaledResolution sr = new ScaledResolution(mc);
        String label = MacroBadgeStyle.LABEL;
        int x = sr.getScaledWidth() - fr.getStringWidth(label) - 4;
        fr.drawStringWithShadow(label, x, 4, MacroBadgeStyle.COLOR_ARGB);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
