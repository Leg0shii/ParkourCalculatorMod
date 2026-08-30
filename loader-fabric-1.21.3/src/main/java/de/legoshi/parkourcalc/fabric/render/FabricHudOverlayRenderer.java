package de.legoshi.parkourcalc.fabric.render;

import de.legoshi.parkourcalc.core.ui.theme.MacroBadgeStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class FabricHudOverlayRenderer {

    public void render(GuiGraphics context, float teleportAlpha) {
        Minecraft client = Minecraft.getInstance();
        Font tr = client.font;
        String label = MacroBadgeStyle.LABEL;
        int x = context.guiWidth() - tr.width(label) - 4;
        context.drawString(tr, label, x, 4, MacroBadgeStyle.COLOR_ARGB, true);

        int noticeColor = MacroBadgeStyle.teleportColorArgb(teleportAlpha);
        if ((noticeColor >>> 24) < 4) return;
        String notice = MacroBadgeStyle.TELEPORT_LABEL;
        int nx = context.guiWidth() - tr.width(notice) - 4;
        context.drawString(tr, notice, nx, 4 + tr.lineHeight + 2, noticeColor, true);
    }
}
