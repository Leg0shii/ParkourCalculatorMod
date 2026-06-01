package de.legoshi.parkourcalc.forge.core.lwjgl2;

import com.github.koxx12dev.fuckyou.ImGuiGL3;
import com.github.koxx12dev.fuckyou.ImGuiLwjgl2;
import de.legoshi.parkourcalc.core.ui.OverlayManager;
import de.legoshi.parkourcalc.core.ui.Settings;
import de.legoshi.parkourcalc.core.ui.theme.Fonts;
import de.legoshi.parkourcalc.core.ui.theme.ThemeManager;
import imgui.ImFont;
import imgui.ImFontConfig;
import imgui.ImFontGlyphRangesBuilder;
import imgui.ImGui;
import imgui.ImGuiIO;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Shared ImGui lifecycle for the Forge 1.8.9 / 1.12.2 loaders, both of which run on
 * LWJGL 2. Knows nothing about Forge, Minecraft, or LWJGL 2 input; call sites feed
 * it the current framebuffer dimensions, and Forge wiring stays per-loader.
 */
public final class Lwjgl2ImGuiHost {

    private static final String FONT_PATH = "/assets/parkourcalculatormod/fonts/JetBrainsMono-Regular.ttf";
    private static final String BOLD_FONT_PATH = "/assets/parkourcalculatormod/fonts/JetBrainsMono-Bold.ttf";
    private static final int BASE_FONT_SIZE = 18;

    private final OverlayManager overlayManager;
    private final Settings settings;
    private final IntConsumer autoScaleResolver;
    private final BooleanSupplier isUiFocused;
    private BooleanSupplier isEditingYaw = () -> false;
    private final ImGuiLwjgl2 imguiLwjgl2 = new ImGuiLwjgl2();
    private final ImGuiGL3 imguiGl3 = new ImGuiGL3();

    private ImFont[] presetFonts;
    private ImFont[] boldPresetFonts;
    private boolean initialized;
    private long lastFrameNanos;
    private int appliedScaleIndex = -1;

    public Lwjgl2ImGuiHost(OverlayManager overlayManager, Settings settings, IntConsumer autoScaleResolver) {
        this(overlayManager, settings, autoScaleResolver, overlayManager::isControlPanelOpen);
    }

    public Lwjgl2ImGuiHost(OverlayManager overlayManager, Settings settings, IntConsumer autoScaleResolver, BooleanSupplier isUiFocused) {
        this.overlayManager = overlayManager;
        this.settings = settings;
        this.autoScaleResolver = autoScaleResolver;
        this.isUiFocused = isUiFocused;
    }

    /** Yaw-cell row nav is driven loader-side via the GuiScreen; suppress the shim's native Tab-out while editing. */
    public void setEditingYawSupplier(BooleanSupplier isEditingYaw) {
        this.isEditingYaw = isEditingYaw;
    }

    /** GuiScreen relays typed chars here; MC drains LWJGL2's queue before the shim sees them. */
    public void forwardChar(char codepoint) {
        if (codepoint == 0) return;
        imguiLwjgl2.charCallback(codepoint);
    }

    public void renderFrame(int displayWidth, int displayHeight) {
        autoScaleResolver.accept(displayHeight);
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

        ImGuiIO modifierIo = ImGui.getIO();
        modifierIo.setKeyCtrl(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL));
        modifierIo.setKeyShift(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
        modifierIo.setKeyAlt(Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU));

        // imguiLwjgl2 polls LWJGL2 directly; pinned overlays would still see play-mode clicks.
        if (!isUiFocused.getAsBoolean()) {
            ImGuiIO io = ImGui.getIO();
            io.setMousePos(-Float.MAX_VALUE, -Float.MAX_VALUE);
            for (int i = 0; i < 5; i++) {
                io.setMouseDown(i, false);
            }
        }
        // Always drain so the delta doesn't accumulate while play mode hides the UI.
        int dwheel = Mouse.getDWheel();
        if (isUiFocused.getAsBoolean() && dwheel != 0) {
            ImGuiIO io = ImGui.getIO();
            io.setMouseWheel(io.getMouseWheel() + dwheel / 120f);
        }

        if (isEditingYaw.getAsBoolean()) {
            ImGui.getIO().setKeysDown(Keyboard.KEY_TAB, false);
        }
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
        if (newIdx < 0) newIdx = Settings.DEFAULT_SCALE_INDEX;
        ThemeManager.apply(Settings.PRESET_SCALES[newIdx]);
        ImGui.getIO().setFontDefault(presetFonts[newIdx]);
        Fonts.setBoldFont(boldPresetFonts[newIdx]);
        appliedScaleIndex = newIdx;
    }

    private void configurePresetFonts() {
        ImGuiIO io = ImGui.getIO();
        io.getFonts().clear();

        short[] glyphRanges = buildGlyphRanges();
        byte[] fontData = readFontBytes(FONT_PATH);
        byte[] boldFontData = readFontBytes(BOLD_FONT_PATH);

        ImFontConfig config = new ImFontConfig();
        config.setGlyphRanges(glyphRanges);

        presetFonts = new ImFont[Settings.PRESET_SCALES.length];
        boldPresetFonts = new ImFont[Settings.PRESET_SCALES.length];
        for (int i = 0; i < Settings.PRESET_SCALES.length; i++) {
            int px = Math.round(BASE_FONT_SIZE * Settings.PRESET_SCALES[i]);
            presetFonts[i] = io.getFonts().addFontFromMemoryTTF(fontData, px, config);
            boldPresetFonts[i] = io.getFonts().addFontFromMemoryTTF(boldFontData, px, config);
        }

        io.getFonts().build();
        config.destroy();
    }

    private static short[] buildGlyphRanges() {
        ImFontGlyphRangesBuilder builder = new ImFontGlyphRangesBuilder();
        builder.addRanges(ImGui.getIO().getFonts().getGlyphRangesDefault());
        return builder.buildRanges();
    }

    private static byte[] readFontBytes(String path) {
        try (InputStream fontStream = Lwjgl2ImGuiHost.class.getResourceAsStream(path)) {
            Objects.requireNonNull(fontStream, "Font not found: " + path);
            ByteArrayOutputStream buf = new ByteArrayOutputStream(fontStream.available());
            byte[] chunk = new byte[8192];
            int n;
            while ((n = fontStream.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            return buf.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load font: " + path, e);
        }
    }
}
