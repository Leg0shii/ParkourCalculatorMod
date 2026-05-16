package de.legoshi.parkourcalc.forge.core.lwjgl2;

import com.github.koxx12dev.fuckyou.ImGuiGL3;
import com.github.koxx12dev.fuckyou.ImGuiLwjgl2;
import de.legoshi.parkourcalc.core.ui.OverlayManager;
import de.legoshi.parkourcalc.core.ui.UiSettings;
import imgui.ImGui;

/**
 * Shared ImGui lifecycle for the Forge 1.8.9 / 1.12.2 loaders, both of which run on
 * LWJGL 2. Knows nothing about Forge, Minecraft, or LWJGL 2 input; call sites feed
 * it the current framebuffer dimensions, and Forge wiring stays per-loader.
 */
public final class Lwjgl2ImGuiHost {

    private final OverlayManager overlayManager;
    private final ImGuiLwjgl2 imguiLwjgl2 = new ImGuiLwjgl2();
    private final ImGuiGL3 imguiGl3 = new ImGuiGL3();

    private boolean initialized;
    private long lastFrameNanos;

    public Lwjgl2ImGuiHost(OverlayManager overlayManager) {
        this.overlayManager = overlayManager;
    }

    /** GuiScreen relays typed chars here; MC drains LWJGL2's queue before the shim sees them. */
    public void forwardChar(char codepoint) {
        if (codepoint == 0) return;
        imguiLwjgl2.charCallback(codepoint);
    }

    public void renderFrame(int displayWidth, int displayHeight) {
        ensureInitialized();

        long now = System.nanoTime();
        float deltaSeconds = Math.max(1e-4f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;

        // Shim's newFrame signature is (displayWidth, displayHeight, deltaTime). Wrong
        // order silently breaks rendering: ImGuiGL3.renderDrawData early-returns when
        // displaySize * framebufferScale rounds to zero, even though ImGui still
        // generates vertices. Call this from a render hook that fires while MC's main
        // framebuffer is still bound (Forge 1.8.9 / 1.12.2: RenderTickEvent.END), so
        // our draws end up in framebufferMc and get composited by its later blit.
        imguiLwjgl2.newFrame(displayWidth, displayHeight, deltaSeconds);
        ImGui.newFrame();
        overlayManager.render(ImGui.getIO());
        ImGui.render();
        imguiGl3.renderDrawData(ImGui.getDrawData());
    }

    private void ensureInitialized() {
        if (initialized) return;
        ImGui.createContext();
        imguiLwjgl2.init();
        imguiGl3.init();
        ImGui.getStyle().scaleAllSizes(UiSettings.SCALE);
        ImGui.getIO().setFontGlobalScale(UiSettings.SCALE);
        lastFrameNanos = System.nanoTime();
        initialized = true;
    }
}
