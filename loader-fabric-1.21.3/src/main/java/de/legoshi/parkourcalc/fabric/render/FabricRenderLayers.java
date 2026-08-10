package de.legoshi.parkourcalc.fabric.render;

import net.minecraft.client.renderer.RenderType;

public final class FabricRenderLayers {

    private FabricRenderLayers() {
    }

    public static final RenderType LINES = RenderType.lines();

    public static final RenderType TRANSLUCENT_FACES = RenderType.debugQuads();
}
