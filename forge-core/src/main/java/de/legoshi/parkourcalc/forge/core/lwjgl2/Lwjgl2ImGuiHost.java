package de.legoshi.parkourcalc.forge.core.lwjgl2;

import com.github.koxx12dev.fuckyou.ImGuiGL3;
import com.github.koxx12dev.fuckyou.ImGuiLwjgl2;
import de.legoshi.parkourcalc.core.ui.OverlayManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import imgui.ImFont;
import imgui.ImFontConfig;
import imgui.ImFontGlyphRangesBuilder;
import imgui.ImGui;
import imgui.ImGuiIO;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Shared ImGui lifecycle for the Forge 1.8.9 / 1.12.2 loaders, both of which run on
 * LWJGL 2. Knows nothing about Forge, Minecraft, or LWJGL 2 input; call sites feed
 * it the current framebuffer dimensions, and Forge wiring stays per-loader.
 */
public final class Lwjgl2ImGuiHost {

    private static final String FONT_PATH = "/assets/parkourcalculatormod/fonts/JetBrainsMono-Regular.ttf";
    private static final int BASE_FONT_SIZE = 18;

    private final OverlayManager overlayManager;
    private final Settings settings;
    private final ImGuiLwjgl2 imguiLwjgl2 = new ImGuiLwjgl2();
    private final ImGuiGL3 imguiGl3 = new ImGuiGL3();

    private ImFont[] presetFonts;
    private boolean initialized;
    private long lastFrameNanos;
    private int appliedScaleIndex = -1;

    public Lwjgl2ImGuiHost(OverlayManager overlayManager, Settings settings) {
        this.overlayManager = overlayManager;
        this.settings = settings;
    }

    /** GuiScreen relays typed chars here; MC drains LWJGL2's queue before the shim sees them. */
    public void forwardChar(char codepoint) {
        if (codepoint == 0) return;
        imguiLwjgl2.charCallback(codepoint);
    }

    public void renderFrame(int displayWidth, int displayHeight) {
        ensureInitialized();
        applyPendingScale();

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
        configurePresetFonts();
        imguiGl3.init();
        applyScale(settings.scaleIndex);
        lastFrameNanos = System.nanoTime();
        initialized = true;
    }

    private void applyPendingScale() {
        if (settings.scaleIndex == appliedScaleIndex) return;
        applyScale(settings.scaleIndex);
    }

    private void applyScale(int newIdx) {
        float newScale = Settings.PRESET_SCALES[newIdx];
        if (appliedScaleIndex < 0) {
            ImGui.getStyle().scaleAllSizes(newScale);
        } else {
            float oldScale = Settings.PRESET_SCALES[appliedScaleIndex];
            ImGui.getStyle().scaleAllSizes(newScale / oldScale);
        }
        ImGui.getIO().setFontDefault(presetFonts[newIdx]);
        appliedScaleIndex = newIdx;
    }

    private void configurePresetFonts() {
        ImGuiIO io = ImGui.getIO();
        io.getFonts().clear();

        short[] glyphRanges = buildGlyphRanges();
        byte[] fontData = readFontBytes();

        ImFontConfig config = new ImFontConfig();
        config.setGlyphRanges(glyphRanges);

        presetFonts = new ImFont[Settings.PRESET_SCALES.length];
        for (int i = 0; i < Settings.PRESET_SCALES.length; i++) {
            int px = Math.round(BASE_FONT_SIZE * Settings.PRESET_SCALES[i]);
            presetFonts[i] = io.getFonts().addFontFromMemoryTTF(fontData, px, config);
        }

        io.getFonts().build();
        config.destroy();
    }

    private static short[] buildGlyphRanges() {
        ImFontGlyphRangesBuilder builder = new ImFontGlyphRangesBuilder();
        builder.addRanges(ImGui.getIO().getFonts().getGlyphRangesDefault());
        return builder.buildRanges();
    }

    private static byte[] readFontBytes() {
        try (InputStream fontStream = Lwjgl2ImGuiHost.class.getResourceAsStream(FONT_PATH)) {
            Objects.requireNonNull(fontStream, "Font not found: " + FONT_PATH);
            ByteArrayOutputStream buf = new ByteArrayOutputStream(fontStream.available());
            byte[] chunk = new byte[8192];
            int n;
            while ((n = fontStream.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            return buf.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load font: " + FONT_PATH, e);
        }
    }
}
